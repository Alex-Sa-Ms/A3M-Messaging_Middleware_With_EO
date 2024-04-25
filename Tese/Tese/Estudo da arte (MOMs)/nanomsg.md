# nanomsg

[https://nanomsg.org/index.html](https://nanomsg.org/index.html)

[https://nanomsg.org/gettingstarted/index.html](https://nanomsg.org/gettingstarted/index.html)

[https://nanomsg.org/documentation-zeromq.html](https://nanomsg.org/documentation-zeromq.html)

https://github.com/nanomsg/nanomsg (pasta *rfc* pode ser útil)

[https://250bpm.com/blog:17/](https://250bpm.com/blog:17/) (Sobre o BUS pattern) 

[https://250bpm.com/blog:19/](https://250bpm.com/blog:19/)

[https://250bpm.com/blog:24/](https://250bpm.com/blog:24/)

[https://250bpm.com/blog:25/](https://250bpm.com/blog:25/)

## REQ-REP Pattern

- REQ and REP support cancelling the ongoing processing. Simply send a new request without waiting for a reply (in the case of REQ socket) or grab a new request without replying to the previous one (in the case of REP socket).