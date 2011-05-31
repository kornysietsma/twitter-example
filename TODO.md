# TODO

* remove hardcoded port - should be able to get the port from the request
* consider making the initial auth handshake happen lazily, rather than at startup time
* add a proper jetty runner, and maybe a way to build a war file?
* split main file up - though it's nice for examples to be a single file, it's a bit unstructured
* error handling!  Especially, handling simple things like when the user clicks "no" on twitter!
* get rid of stateful sessions - we only write to the session from one spot, so could use default sessions
* consider reverting to non-snapshot version of clj-oauth (first attempt failed, should try again!)
* try to get rid of ruby/node dependencies