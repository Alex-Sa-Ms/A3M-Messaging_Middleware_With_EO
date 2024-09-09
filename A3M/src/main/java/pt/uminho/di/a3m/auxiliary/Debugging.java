package pt.uminho.di.a3m.auxiliary;

public class Debugging {
    private static Object printOrder = new Object();
    public static void printlnOrdered(String s){
        synchronized (printOrder) {
            System.out.println(s);
            System.out.flush();
        }
    }
    public static void printOrdered(String s){
        synchronized (printOrder) {
            System.out.print(s);
            System.out.flush();
        }
    }
}
