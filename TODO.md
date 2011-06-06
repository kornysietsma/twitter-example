# TODO

## important
* use weavejester's suggested routing improvements - currently commented out as I had some problems with them
* consider reverting to non-snapshot version of clj-oauth (first attempt failed, should try again!) and sandbar

## longer term
* refactor - several functions in core.clj are too long
* add more tests
* split main file up - though it's nice for examples to be a single file, it's a bit unstructured
* error handling!
* get rid of stateful sessions - we only write to the session from one spot, so could use default sessions instead of sandbar
* try to get rid of ruby/node dependencies ? coffee at least can be compiled in the browser...