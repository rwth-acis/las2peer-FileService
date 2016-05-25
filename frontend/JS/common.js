
(function() {
  this.module("i5", function() {
    return this.module("las2peer", function() {
      return this.module("messages", function() {

        /*
          Formatting helper
         */
        this.Encoder = (function() {
          function Encoder() {}


          /*
            Escapes text so it does not get executed (transforms to plain text).
           */

          Encoder.escapeUnsafe = function(unsafe) {
            return $('<span></span>').text(unsafe).html();
          };


          /*
            Converts a string to a compatible id, by replacing spaces with _ and removing special characters.
           */

          Encoder.formatAsId = function(text) {
            var rep;
            return rep = text.replace(/\ /g, '_').replace(/\W/g, '');
          };


          /*
            Helps to create various elements of the microblog pages.
           */

          return Encoder;

        })();
        return this.ElementGenerator = (function() {
          function ElementGenerator() {}

          ElementGenerator.generateEntryElements = function(xmlInnerArr, clickevent, parent) {
            var author, div, i, k, text, time,_len;
            
            for (i =  0, _len = xmlInnerArr.length; i < _len; i++) {
              k = xmlInnerArr[i];
              div = document.createElement('div');
              div.className = "blogContainer";
              div.id = k.attr("id");
              author = document.createElement('div');
              author.className = "authorItem";
              $(author).text(k.attr("owner"));
              time = document.createElement('div');
              time.className = "dateItem";
              $(time).text(k.attr("time"));
              $(author).append(time);
              text = document.createElement('div');
              text.className = "textItem";
              text.id = div.id;
              author.id = div.id;
              $(text).text(k.html());
              $(div).append(author);
              $(div).append(text);
              $(div).click(function(event) {
                if (clickevent != null) {
                  clickevent(event.target.id);
                }
              });
              $(parent).append(div);
            }
            
          };

          ElementGenerator.generateMultilineTextfield = function(parent) {
            var textfieldDiv;
            textfieldDiv = document.createElement("div");
            textfieldDiv.id = "textfieldBox";
            textfieldDiv.className = "selectItem multilineText";
            $(textfieldDiv).html('<textarea name="blogEntry" id="enterBlogEntry"' + 'value=""/><input type="button" id="sendEntryButton" name="no" value="Send"></input>');
            $(parent).append(textfieldDiv);
          };

          return ElementGenerator;

        })();
      });
    });
  });

}).call(this);
