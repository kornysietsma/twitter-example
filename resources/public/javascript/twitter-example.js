(function() {
  var TwitterExample, root;
  var __bind = function(fn, me){ return function(){ return fn.apply(me, arguments); }; };
  TwitterExample = (function() {
    function TwitterExample() {
      this.outputElement = '#output';
      this.load_views();
      $.ajaxSetup({
        timeout: 10000
      });
      this.check_status();
    }
    TwitterExample.prototype.check_status = function() {
      var result;
      result = $.getJSON("/auth/status.json");
      result.done(__bind(function(data) {
        this.renderView(this.outputElement, "tweets", {
          name: data.name
        });
        return this.fetch_tweets();
      }, this));
      return result.fail(__bind(function(data) {
        return this.auth_error(data);
      }, this));
    };
    TwitterExample.prototype.fetch_tweets = function() {
      var result;
      result = $.getJSON("/auth/tweets.json");
      result.done(__bind(function(data) {
        return this.renderView(this.outputElement, "tweets", {
          name: data.name,
          tweets: data.tweets
        });
      }, this));
      return result.fail(__bind(function(data) {
        return this.auth_error(data);
      }, this));
    };
    TwitterExample.prototype.auth_error = function(data) {
      var payload;
      if (data.status === 401) {
        payload = JSON.parse(data.responseText);
        if (!((payload != null) && payload.authUrl)) {
          return this.renderView(this.outputElement, "error", {
            message: "Unexpected error payload:",
            data: JSON.stringify({
              payload: payload,
              response: data
            }, null, 4)
          });
        } else {
          return this.renderView(this.outputElement, "noauth", payload);
        }
      } else {
        return this.renderView(this.outputElement, "error", {
          message: "Unexpected error:",
          data: JSON.stringify(data, null, 4)
        });
      }
    };
    TwitterExample.prototype.load_views = function() {
      var that;
      that = this;
      that.views = {};
      return $(".view-template").each(function() {
        var name;
        name = $(this).attr("data-name");
        return that.views[name] = Handlebars.compile($(this).html());
      });
    };
    TwitterExample.prototype.renderView = function(element, name, data) {
      var view;
      if (!this.views[name]) {
        throw new Error("no such view: " + name);
      }
      view = this.views[name](data);
      return $(element).html(view);
    };
    return TwitterExample;
  })();
  root = typeof global !== "undefined" && global !== null ? global : window;
  root.TwitterExample = TwitterExample;
  $(function() {
    return root.twitterExample = new TwitterExample;
  });
}).call(this);
