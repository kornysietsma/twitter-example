# TODO

* remove hardcoded port - should be able to get the port from the request (though this will require lazy fetching of request tokens)
* consider making the initial auth handshake happen lazily, rather than at startup time
* add a proper jetty runner, and maybe a way to build a war file?
* split main file up - though it's nice for examples to be a single file, it's a bit unstructured