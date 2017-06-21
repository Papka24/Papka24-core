/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

// Transform input field to input filed with autocomplete
// Works only with utils.js
var autocompleteHelper = null;
autocompleteHandlers = {
    showHelp:function(){
        if (this.ac.altList.length<1){
            autocompleteHandlers.hideHelp();
            return;
        }
        // Detect incorrect position of input element
        var pos = getOffset(this);

        autocompleteHelper.style.left=pos.left+'px';
        autocompleteHelper.style.top=pos.top+this.offsetHeight+'px';
        autocompleteHelper.style.width=this.offsetWidth-2+'px';
        autocompleteHelper.style.display='block';
        autocompleteHandlers.updateAlternatives.call(this);
    },
    updateAlternatives:function(){
    	window.AutocompleteCurrentInputField = this;
        var vizSize = this.ac.altList.length;
        if (vizSize>6){
            vizSize = 6;
        }
        if (this.ac.listType==2) {
            vizSize*=41;
        } else {
            vizSize*=41;
        }
        var items="";
        var value;
        var info;
        var selected;
        for (var i in this.ac.altList){
            if (this.ac.altList.hasOwnProperty(i)) {
                if (this.ac.listType == 2) {
                    value = this.ac.altList[i][0];
                    info = this.ac.altList[i][2];
                    selected = this.ac.altListIndex == i;
                    items += '<div class="' + (selected ? 'sel' : '');
                    items += (info == "" ? '' : ' ext');
                    items += '" onmousedown="AutocompleteCurrentInputField.value=\'' + value.replace("<b>", "").replace("</b>", "") + '\';if(typeof AutocompleteCurrentInputField.ac.onEnterEvent === \'function\'){AutocompleteCurrentInputField.ac.onEnterEvent()}">' + value;
                    items += (info == "" ? '' : ('<span>' + this.ac.altList[i][2] + '</span>')) + '</div>';
                } else {
                    value = this.ac.altList[i];
                    if (this.ac.altListIndex == i) {
                        items += '<div class="sel" ' +
                            'onmousedown="AutocompleteCurrentInputField.value=\'' +
                                value.replace("<b>", "").replace("</b>", "") + '\';' +
                                'if(typeof AutocompleteCurrentInputField.ac.onEnterEvent === \'function\'){AutocompleteCurrentInputField.ac.onEnterEvent()}' +
                            '">' 
                            + value 
                            + '</div>';
                    } else {
                        items += '<div ' +
                            'onmousedown="AutocompleteCurrentInputField.value=\'' + 
                                value.replace("<b>", "").replace("</b>", "") + '\';' +
                                'if(typeof AutocompleteCurrentInputField.ac.onEnterEvent === \'function\'){AutocompleteCurrentInputField.ac.onEnterEvent()}' +
                            '">' 
                        + value 
                        + '</div>';
                    }
                }
            }
        }
        // change scroll
        if (this.ac.altListIndex>1){
            if (this.ac.listType==2) {
                autocompleteHelper.scrollTop = this.ac.altListIndex*40;
            } else {
                autocompleteHelper.scrollTop = this.ac.altListIndex*25;
            }
        } else {
            autocompleteHelper.scrollTop = 0;
        }
        autocompleteHelper.innerHTML = items;
        autocompleteHelper.style.height = vizSize + 'px';
    },
    hideHelp:function(){
        if (this && this.ac && this.ac.mode == 1){
           var errorFlag=this.value.length>0;
           if (errorFlag){
                for (var i=this.ac.list.length-1; i>0;i--){
                    if (this.ac.listType==0){
                        if (this.ac.list[i]==this.value){
                            errorFlag=false;
                            break;
                        }
                    } else {
                        if (this.ac.list[i][0]==this.value){
                            errorFlag=false;
                            break;
                        }
                    }
                }
            }
            if (errorFlag) {
                addClass(this,"errorField");
            }else{
                rmClass(this,"errorField");
            }
        }
        autocompleteHelper.style.display='';
    },
    keypress:function (e) {

        var elm = (this.setSelectionRange) ? e.which : e.keyCode;

    	if (e.charCode && elm == 0){
    		elm = e.charCode;
    	}

        if ((!this) || (!e) || (this.ac.list.length == 0)) {
            autocompleteHandlers.hideHelp();
            return;
        }

        if (this.value.length == 0) {
            autocompleteHandlers.hideHelp();
            return;
        }

        // backspace handler
        var revertChanges = false;

        // ignore shift key
        if (elm == 16){
            return;
        }

        // handle enter
        if (elm == 13){
            if (typeof this.ac.onEnterEvent === "function") {
                if (this.ac.newValue!=""){
                    this.value = this.ac.newValue;
                    if(this.ac.newId!=-1){
                        this.ac.onEnterEvent(this.ac.newId);
                    } else {
                        this.ac.onEnterEvent(this.ac.newValue);
                    }

                } else {
                    this.ac.onEnterEvent(this.value);
                }
            }
        }
        this.ac.newValue = "";

        // handle escape
        if (elm == 27){
            if (this.ac.onEscapeEvent) {
                this.ac.onEscapeEvent(this.value);
            }
        }


        //handle backspace
        if (elm==8){
            if (this.lastResult>0){
        	    this.value = this.value.substring(0, this.value.length-1);
            }
            revertChanges = true;
        } else if (elm!=0 && ((elm < 32) || (elm >= 33 && elm <= 46) || (elm >= 112 && elm <= 123))) {
            // change alt index
            if (elm == 40){
                this.ac.altListIndex++;
                if (this.ac.altListIndex>=this.ac.altList.length){
                    this.ac.altListIndex=0;
                }
                if (this.ac.listType>0) {
                    this.value=this.ac.altList[this.ac.altListIndex][0].replace("<b>","").replace("</b>","");
                    this.value=this.ac.altList[this.ac.altListIndex][2].replace("<b>","").replace("</b>","");
                } else {
                    this.value=this.ac.altList[this.ac.altListIndex].replace("<b>","").replace("</b>","");
                }
                autocompleteHandlers.updateAlternatives.call(this);
            } else if(elm == 38){
                this.ac.altListIndex--;
                if (this.ac.altListIndex<0){
                    this.ac.altListIndex=this.ac.altList.length-1;
                }
                if (this.ac.listType>0) {
                    this.value=this.ac.altList[this.ac.altListIndex][0].replace("<b>","").replace("</b>","");
                    this.value=this.ac.altList[this.ac.altListIndex][2].replace("<b>","").replace("</b>","");
                } else {
                    this.value=this.ac.altList[this.ac.altListIndex].replace("<b>","").replace("</b>","");
                }
                autocompleteHandlers.updateAlternatives.call(this);
            } else {
                // ignore shift key
                autocompleteHandlers.hideHelp.call(this);
            }
            return;
        }
        if (this.value.length==0){
            autocompleteHandlers.hideHelp.call(this);
            return;
        }
        var ini=null;
        var rng=null;
        if (this.createTextRange) {

        	if ( document.body.createTextRange ) {
        		// hack for IE
        		rng = document.body.createTextRange();
        		rng.moveToElementText( this );
        	} else {
        		// for other browser
        		rng = document.selection.createRange();
            }

        	if (rng.parentElement() == this) {
            	elm = rng.text;
            	ini = this.value.lastIndexOf(elm);
        	}
        } else if (this.setSelectionRange) {
            ini = this.selectionStart;
        }

        var pattern = new RegExp("^"+this.value,"i");
        var results=[];
        var i;
        for ( i = 0; i < this.ac.list.length; i++) {
            if (this.ac.listType==0) {
                elm = this.ac.list[i];
            } else {
                elm = this.ac.list[i][0];
            }
            if (pattern.test(elm)) {
                results.push(this.ac.list[i]);
            }
        }

        // find substring not from start
        var find1;
        var find2;
        if (results.length==0){
            pattern = new RegExp(this.value,"i");
            for (i = 0; i < this.ac.list.length; i++) {
                if (this.ac.listType==0) {
                    find1 = this.ac.list[i];
                } else {
                    find1 = this.ac.list[i][0];
                    find2 = this.ac.list[i][2];
                }
                var result1 = find1.match(pattern);
                var result2;
                var j1=-1;
                var j2=-1;
                if (result1 == null && this.ac.listType>0){
                    result2 = find2.match(pattern);
                }
                if (result1 != null || result2 != null) {
                    if (result1 != null){
                        j1 = find1.indexOf(result1[0]);
                    }
                    var newElement = clone(this.ac.list[i]);
                    if (this.ac.listType==0){
                        newElement = newElement.toString().insertAt("<b>", j1).insertAt("</b>", j1 + result1[0].length + 3);
                    } else {
                        if (result1 != null){
                            newElement[0] = newElement[0].insertAt("<b>", j1).insertAt("</b>", j1 + result1[0].length + 3);
                        } else {
                            j2 = find2.indexOf(result2[0]);
                            newElement[2] = newElement[2].insertAt("<b>", j2).insertAt("</b>", j2 + result2[0].length + 3);
                        }
                    }
                    results.push(newElement);
                }
            }
            if (results[0]){
                this.ac.newValue = results[0][0].replace("<b>","").replace("</b>","");
                if (newElement.length==4){
                    this.ac.newId = newElement[3];
                } else {
                    this.ac.newId = -1;
                }
            } else {
                this.ac.newId = -1;
                this.ac.newValue="";
            }
        } else {
            if (this.ac.listType>0) {
                results.sort(function(a, b){if (a[1]==b[1])return 0; if (a[1]>b[1]) return -1; else return 1;});
                this.ac.newValue=results[0][0];
            } else {
                this.value=results[0];
            }

            if (results.length==4){                
                this.ac.newId = results[3];
            } else {
                this.ac.newId = -1;
            }
        }
        this.ac.altList = results;

        if (this.ac.listType==0) {
            setCaretPosition(this,ini,this.value.length);
        }
        this.lastResult = this.value.length - ini;
        this.ac.altListIndex=0;
        autocompleteHandlers.showHelp.call(this);
        if (revertChanges){
            return false;
        }
    }
};

/**
 * Add autocomplete to input element
 * element - input object
 * list - 0 - 1-d array
 *        1 - 2-d array (name + priority)
 *        2 - 2-d array (name + priority + help information)
 * mode - 0 - simple mode
 *        1 - select only from list (strict mode)
 */
function Autocomplete(element, list, mode, onEnterEvent, onEscapeEvent) {
    // link this obj with text field
    element.ac = this;
    this.mode = mode;
    this.list = list;
    if (list.length==0){
        list.push("");
    }
    if (typeof onEnterEvent === "function"){
        this.onEnterEvent = onEnterEvent;
    }
    if (typeof onEscapeEvent === "function"){
        this.onEscapeEvent = onEscapeEvent;
    }

    this.listType = 0;
    if (typeof list[0] !== 'string') {
        this.listType = 1;
        if (list[0].length == 3 || list[0].length == 4) {
            this.listType = 2;

        }
    }
    element.addEventListener("keyup", autocompleteHandlers.keypress, false);
    element.addEventListener("blur", autocompleteHandlers.hideHelp, false);

    // save position
    var pos = getOffset(element);
    this.x = pos.left;
    this.y = pos.top;
    this.height = element.offsetHeight;
    this.width = element.offsetWidth;

    //set alternatives
    this.altList = [];
    this.altListIndex=0;

    // first helper init
    if (autocompleteHelper==null){
        var h = document.createElement("div");
        h.setAttribute('id','autocompleteHelper');
        document.body.appendChild(h);
        autocompleteHelper = h;
    }
}