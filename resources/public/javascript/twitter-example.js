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
      this.setup_bindings();
      this.check_status();
    }
    TwitterExample.prototype.setup_bindings = function() {
      return $("#initialize").live("submit", __bind(function(event) {
        return this.initialize(event);
      }, this));
    };
    TwitterExample.prototype.check_status = function() {
      var result;
      result = $.getJSON("/auth/status.json");
      result.done(__bind(function(data) {
        this.render_view(this.outputElement, "tweets", {
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
        return this.render_view(this.outputElement, "tweets", {
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
        if (!((payload != null) && (payload.authUrl != null))) {
          return this.show_error("Unexpected error payload:", {
            payload: payload,
            response: data
          });
        } else {
          return this.render_view(this.outputElement, "noauth", payload);
        }
      } else {
        return this.show_error("Unexpected error:", data);
      }
    };
    TwitterExample.prototype.show_error = function(message, data) {
      if (data != null) {
        return this.render_view(this.outputElement, "error", {
          message: message,
          data: JSON.stringify(data, null, 4)
        });
      } else {
        return this.render_view(this.outputElement, "error", {
          message: message,
          data: nil
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
    TwitterExample.prototype.render_view = function(element, name, data) {
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
