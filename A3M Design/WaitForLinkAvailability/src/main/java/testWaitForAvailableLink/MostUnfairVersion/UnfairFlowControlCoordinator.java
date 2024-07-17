package testWaitForAvailableLink.MostUnfairVersion;

import pt.uminho.di.a3m.SocketIdentifier;
import testWaitForAvailableLink.IFlowControlCoordinator;
import testWaitForAvailableLink.LinkClosedException;


import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Unfair version, which allows consuming credits at any time as long as there are credits to be consumed.
 */
public class UnfairFlowControlCoordinator implements IFlowControlCoordinator {

    Map<SocketIdentifier, UnfairFlowControlState> linkStates = new HashMap<>();
    Set<Long> genRequests = new HashSet<>(); // general requests
    //int genRequests = 0; // number of general requests
    Deque<SocketIdentifier> genSigs = new LinkedList<>(); // signals to general threads
    Condition genAvailCond; // general requests availability condition

    // TODO - 1. test
    //        2. Describe methods in interface, like wait should consume credit, etc.
    //        3. Correct FlowControlManager to follow those semantics.
    //        4. Edit socket constructor to allow selecting different types of flow control managers.
    //        5. Now there is a socket that can select a fairish version and an unfair version.

    // TODO - É preciso ter em atenção o throw if none. Fazer o mesmo que quando um link é eliminado, só que
    //      caso já não existam links, acordar também todas as general threads.
    // Como resolver o problema de permitir esperar sem consumir crédito?
    // R:
    //      (1) Uma thread ao sair com sucesso de um método de espera deve registar
    // um objeto de reserva contendo o seu id de thread e o id do link.
    //      (2) Ao ser invocado qualquer método para consumir um credito, deve verificar se
    // se o crédito já foi reservado. Caso exista o objeto de reserva e este corresponda ao id de thread
    // e do link, então consome-se o crédito e elimina-se o objeto de reserva. Caso contrário, executa-se
    // o procedimento normal.
    //      (3) Qualquer invocacao de um método público relacionado com créditos deverá verificar se existe um objeto de reserva.
    // Se este não corresponder à thread atual, deve ser eliminado e resultar na sinalização de uma thread para o link identificado.
    //
    // PROBLEMA DA SOLUCAO: Uma thread que poderia ser sinalizada vai "passar fome" (starvation) até que um método relacionado com créditos seja
    //  invocado.
    // COMO RESOLVER: Manter o objeto de reserva, mas neste caso com um propósito de autorização.
    // Em vez de não consumir o crédito e fazer uma reserva, passa-se a consumir o crédito e
    // pode-se cancelar o consumo desse crédito (resultando na sinalização de outra thread).
    //
    // PROBLEMA DA SOLUCAO 2: Quando e como se remove o objeto de autorização?
    // COMO RESOLVER: Através de qualquer método relacionado com espera ou consumo de crédito. Sendo eliminado
    // ou substituido aquando da invocacao desses metodos.
    //
    // SOLUCAO 3: As operações de espera consomem créditos (de forma a reservá-los) e
    // a implementação base do socket regista as threads que "reservaram" créditos, i.e.
    // quando métodos "wait" foram utilizados. Aquando do uso de um método de envio deve
    // verificar-se em primeiro lugar se existe alguma "reserva". A implementação base do socket
    // deve também permitir cancelar o consumo de um crédito, utilizando o set de reservas para
    // verificar se efetivamente a thread pode cancelar um consumo e caso possa deve fazer-se
    // replenish de 1 crédito para o link em questão. As reservas podem ser de objetos com pares (id thread, id link).
    //




    // TODO -
    //  MELHORIA:
    //  Extrair lógica de espera para fora do gestor de controlo de fluxo. Essencialmente fica o mesmo, mas
    //  com nomes diferentes, tanto o da classe como possivelmente dos créditos, etc.
    //  Isto permite que os sockets tenham uma queue de mensagens a enviar para cada link, sendo possível
    //  enviar logo que sejam recebidos créditos para o link. Se o tamanho da queue já tiver sido excedido,
    //  aí sim entraria em uso o mecanismo de sinalizar threads para poderem enviar mensagens se pretenderem.
    //  Neste caso, sendo o ideal consumir créditos e caso não se pretenda enviar mensagem, informar tal falta
    //  de intenção através de um método "creditNotConsumed()" por exemplo.
    //  .
    //  QUESTAO: Remoção de créditos ("diminuição do tamanho da janela") pode influenciar?
    //  R: A versão "MostUnfair" deve permitir remover créditos facilmente. A versão "Fairish" pode exigir uma
    //  maior lógica mas deve continuar a ser possível. Os créditos nestes "schedulers" devem ter como valor
    //  mínimo 0, já que não faz sentido um scheduler ter cŕeditos negativos.

    public void newLinkState(SocketIdentifier sid, int credits, Condition availCond) {
        if (linkStates.containsKey(sid))
            throw new IllegalStateException("A link state already exists associated with the given socket identifier.");
        else {
            UnfairFlowControlState linkState = new UnfairFlowControlState(sid, credits, availCond);
            linkStates.put(sid, linkState);
            if (credits > 0) {
                signalWaitingThreads(sid, linkState, credits);
            }
        }
    }

    
    public void closeAndRemoveLinkState(SocketIdentifier sid) {
        UnfairFlowControlState linkState = linkStates.remove(sid);
        linkState.closed = true;
        genSigs.removeIf(aux -> aux.equals(sid));
        linkState.availCond.signalAll();
    }

    
    public int getCredits(SocketIdentifier sid) {
        UnfairFlowControlState linkState = getLinkState(sid);
        return linkState.credits;
    }

    @Override
    public int tryConsumeCredits(SocketIdentifier sid, int credits) {
        return tryConsumeCredits(sid, getLinkState(sid), credits);
    }

    @Override
    public int tryConsumeCredit(SocketIdentifier sid) {
        return tryConsumeCredits(sid, getLinkState(sid), 1);
    }

    private int tryConsumeCredits(SocketIdentifier sid, UnfairFlowControlState linkState, int credits){
        int r = linkState.credits - credits;
        if(r < 0) return r;
        linkState.credits = r;
        return r;
    }

    private boolean tryConsumeCredit(SocketIdentifier sid, UnfairFlowControlState linkState){
        return tryConsumeCredits(sid, linkState, 1) >= 0;
    }

    
    public int replenishCredits(SocketIdentifier sid, int credits) {
        UnfairFlowControlState linkState = getLinkState(sid);
        // adds credits
        linkState.credits += credits;
        if(credits > 0)
            signalWaitingThreads(sid, linkState, credits);
        // returns the current amount of credits held by the link
        return linkState.credits;
    }

    
    public boolean isLinkAvailable(SocketIdentifier sid) {
        return getLinkState(sid).credits > 0;
    }
    
    private boolean internalWaitForLinkAvailability(SocketIdentifier sid, Long timeout) throws InterruptedException, LinkClosedException {
        long timeoutTime = 0L;
        if(timeout != null)
            timeoutTime = calculateTimeoutTime(timeout);

        // If the link has credits, then return immediatelly
        UnfairFlowControlState linkState = getLinkState(sid);
        if(tryConsumeCredit(sid, linkState))
            return true;
        
        try {
            boolean proceed = false, timedOut = false;
            while (!proceed && !timedOut) {
                // Create and ensure its availability request is registered
                createLinkSpecificRequest(linkState);
                // waits until signal or timeout
                if(timeout != null) {
                    timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                    timedOut = !linkState.availCond.await(timeout, TimeUnit.MILLISECONDS);
                }else linkState.availCond.await(); 
                // if link is closed, throw exception
                if(linkState.isClosed())
                    throw new LinkClosedException("Link was closed.");
                // try to consume credit
                proceed = tryConsumeCredit(sid, linkState);
            }
            removeLinkSpecificRequest(linkState);
            return proceed;
        }catch (InterruptedException | LinkClosedException e){
            removeLinkSpecificRequest(linkState);
            throw e;
        }
    }

    @Override
    public boolean waitForLinkAvailability(SocketIdentifier sid, long timeout) throws InterruptedException, LinkClosedException {
        return internalWaitForLinkAvailability(sid, timeout);
    }

    @Override
    public void waitForLinkAvailability(SocketIdentifier sid) throws InterruptedException, LinkClosedException {
        internalWaitForLinkAvailability(sid, null);
    }

    private SocketIdentifier internalWaitForAvailableLink(Long timeout) throws InterruptedException {
        long timeoutTime = 0L;
        if(timeout != null)
            calculateTimeoutTime(timeout);

        // Checks if there is any available link that can return immediatelly
        SocketIdentifier sid = getAvailableLink();
        UnfairFlowControlState linkState = linkStates.get(sid);
        if(linkState != null && tryConsumeCredit(sid, linkState))
            return sid;

        try {
            boolean proceed = false,
                    timedOut = false;
            while (!proceed && !timedOut) {
                // Create and ensure its availability request is registered
                createGeneralRequest();
                // waits until signal or timeout
                if(timeout != null) {
                    timeout = Math.max(0L, timeoutTime - System.currentTimeMillis());
                    timedOut = !genAvailCond.await(timeout, TimeUnit.MILLISECONDS);
                }else genAvailCond.await();
                // polls a general signal
                sid = genSigs.poll();
                linkState = linkStates.get(sid);
                proceed = linkState != null && tryConsumeCredit(sid, linkState);
            }
            removeGeneralRequest();
            return sid;
        }catch (InterruptedException ie){
            removeGeneralRequest();
            throw ie;
        }
    }

    @Override
    public SocketIdentifier getAvailableLink(){
        for(Map.Entry<SocketIdentifier, UnfairFlowControlState> entry : linkStates.entrySet()){
            if (entry.getValue().hasCredits())
                return entry.getKey();
        }
        return null;
    }

    @Override
    public SocketIdentifier waitForAvailableLink(long timeout) throws InterruptedException {
        return internalWaitForAvailableLink(timeout);
    }

    @Override
    public SocketIdentifier waitForAvailableLink() throws InterruptedException {
        return internalWaitForAvailableLink(null);
    }

    UnfairFlowControlState getLinkState(SocketIdentifier sid) {
        UnfairFlowControlState linkState = linkStates.get(sid);
        if (linkState == null)
            throw new IllegalArgumentException("No state found for the given socket identifier.");
        else
            return linkState;
    }

    private long calculateTimeoutTime(long timeout){
        // validates timeout and calculates timeout time
        if(timeout < 0L)
            throw new IllegalArgumentException("Timeout must not be a negative.");
        long timeoutTime = System.currentTimeMillis() + timeout;
        if(timeoutTime < 0L)
            timeoutTime = Long.MAX_VALUE;
        return timeoutTime;
    }

    // Creates request for a specific socket availability
    private void createLinkSpecificRequest(UnfairFlowControlState linkState){
        linkState.requests.add(Thread.currentThread().threadId());
    }

    // Create request for any socket availability
    private void createGeneralRequest(){
        genRequests.add(Thread.currentThread().threadId());
    }
    
    // Aborts a link specific request.
    private void removeLinkSpecificRequest(UnfairFlowControlState linkState){
        linkState.requests.remove(Thread.currentThread().threadId());
    }

    // Aborts a general request
    private void removeGeneralRequest(){
        genRequests.remove(Thread.currentThread().threadId());
    }
    
    private void signalWaitingThreads(SocketIdentifier sid, UnfairFlowControlState linkState, int credits) {
        int nSignals = Math.min(credits,genRequests.size() + linkState.requests.size());

        while (nSignals > 0){
            if(linkState.sigSpecific){
                if(!linkState.requests.isEmpty()) {
                    nSignals--;
                    removeFirst(linkState.requests);
                    linkState.availCond.signal();
                    linkState.sigSpecific = false;
                }else{
                    while (nSignals > 0) {
                        nSignals--;
                        removeFirst(genRequests);
                        genSigs.add(sid);
                        genAvailCond.signal();
                    }
                }
            }
            else{
                if(!genRequests.isEmpty()) {
                    nSignals--;
                    removeFirst(genRequests);
                    genSigs.add(sid);
                    genAvailCond.signal();
                    linkState.sigSpecific = true;
                }else{
                    while (nSignals > 0) {
                        nSignals--;
                        removeFirst(linkState.requests);
                        genAvailCond.signal();
                    }
                }
            }
        }

    }

    private <T> T removeFirst(Collection<T> collection){
        return collection.iterator().next();
    }
}