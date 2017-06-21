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

/*fixme should have description*/
var FancyAutocomplete = function(mainArr, target, keyObject){
    this.className = "fancyAutoComplete";
    this.mainArr = mainArr || []; /*FIXME should be {arr : [{}, {}], scheme : []} or [] no need for keyObject*/
    this.target = target;
    this.keys = keyObject.renderKeys;
    this.valueKeys = keyObject.valueKeys || this.keys;
    this.searchKeys = keyObject.searchKeys || this.keys;
    this.currentPosition = 1;
    this.foundArr = [];
    this.foundArrLength = 0;
    this.selectedValues = [];

    if(hasClass(this.target, this.className)){ /*FIXME old instance should be uninitialized*/
        return;
    }

    if(!this.mainArr.length){
        throw new Error('Main array should be specified');
    }
    if(!this.target){
        throw new Error('Target should be specified');
    }
    this.autocompleteWrap = target.parentElement.querySelector('.autoCompleteWrap');
    this.addWraps = function(){
        addClass(target, this.className);
        if(!this.autocompleteWrap){
            this.autocompleteWrap = document.createElement('div');
            addClass(this.autocompleteWrap, 'autoCompleteWrap');
            this.target.parentElement.appendChild(this.autocompleteWrap);
        }else{
            this.autocompleteWrap.innerHTML = '';
        }
    };
    this.setCurrentPosition = function(position){
        if(position > this.foundArrLength){
            this.currentPosition = 1;
        }else if(position < 1){
            this.currentPosition = this.foundArrLength;
        }else{
            this.currentPosition = position;
        }
    };
    this.moveActiveElement = function(flag){
        flag = !flag; // true - scroll, false - don't scroll
        var new_element = this.autocompleteWrap.querySelector('div:nth-child('+this.currentPosition+')');
        rmClass(this.autocompleteWrap.querySelector('.js-active'), 'js-active');
        addClass(new_element, 'js-active');
        if(flag){
            new_element.scrollIntoView(false);
        }
    };
    this.handleKeyUps = function(e){
        var res = false, //preventDefault flag
            keyCode = e.keyCode,
            flag = hasClass(this.target, 'js-active');
        if(keyCode === 38 || keyCode === 40){
            //do nothing - handled in handleKeyDowns
        }else if((keyCode === 9 || keyCode === 13)) { //handle TAB && ENTER
            if(flag){
                this.setTargetValue(this.autocompleteWrap.querySelector('.js-active').getAttribute('data-value'));
            }else{
                this.callbackSearch();
            }

        }else{ //any other key - let it be
            var temp = this.setSelectedValues();
            if(!temp){
                this.callbackSearch();
            }
            this.search();
            res = true;
        }
        if(!res && e.preventDefault){
            e.preventDefault();
        }
        return res;
    };
    this.handleKeyDowns = function(e){
        if(!hasClass(this.target, 'js-active')){return false;}
        var knownCodes = [38,40,9,13,27],
            keyCode = e.keyCode;
        if(knownCodes.indexOf(keyCode) != -1){
            e.preventDefault();
            stopBubble(e);
            if(keyCode === 38){ // handle UP arrow
                this.setCurrentPosition(--this.currentPosition);
                this.moveActiveElement();
            }else if(keyCode === 40){ //handle DOWN arrow
                this.setCurrentPosition(++this.currentPosition);
                this.moveActiveElement();
            }
        }
    };
    this.handleKeyPress = function(e){
        var temp;
        if(e.charCode === 44){ //comma
            temp = this.setSelectedValues();
            this.callbackSearch(temp);
        }
    };
    /*fixme rewrite fillAutocompleteWrap
        this.getValuesFromKeys = function(obj){
            if(typeof obj === 'string'){return obj;}
            var res = [];
            foreach(obj, function (v, i) {
                if(this.valueKeys.indexOf(i) != -1){
                    res.push(v);
                }
            }, this);
            return res.join(' ');
        };
    */
    this.getValuesFromKeys = function(obj){
        if(typeof obj === 'string'){return obj;}
        var res = [],
            temp;
        for(temp in obj){
            if(this.valueKeys.indexOf(temp) != -1){
                res.push(obj[temp]);
            }
        }
        return res.join(' ').toLowerCase();
    };
    this.fillAutocompleteWrap = function(arr, el, keys, searchQuery){
        if(!arr || arr.length === 0){return false;}
        var node = document.createElement('div'),
            element = arr.shift(),
            innerHTML = '',
            index,
            temp;
        keys = window.isArray(keys) ? keys : [keys];
        if(keys.length && keys[0]){ //non-empty array
            node.setAttribute('data-value',
                this.getValuesFromKeys(element)
            );
            for(var i = 0, length = keys.length; i < length; i++){
                temp = element[keys[i]];
                index = temp.toLowerCase().indexOf(searchQuery.toLowerCase());
                if(index != -1){
                    temp = temp.substring(0, index)+'<b>'+temp.substring(index, index+searchQuery.length)+'</b>' + temp.substring(index+searchQuery.length, temp.length);
                }
                innerHTML += '<p>'+temp+'</p>';
            }
        }else{ // keys = falsy or empty
            node.setAttribute('data-value', JSON.stringify(element));
            innerHTML = JSON.stringify(element);
            innerHTML = innerHTML.replace(/^"(.*)"$/, '$1');
            index = innerHTML.indexOf(searchQuery);
            innerHTML = innerHTML.substring(0, index)+'<b>'+innerHTML.substring(index, index+searchQuery.length)+'</b>' + innerHTML.substring(index+searchQuery.length, innerHTML.length);
        }
        node.innerHTML = innerHTML;
        node.insertBefore(this.addImage(element.email), node.childNodes[0]); //FIXME отрефакторить, должно приходить параметром)
        el.appendChild(node);
        this.fillAutocompleteWrap(arr, el, keys, searchQuery);
    };
    this.addImage = function(email){ //FIXME отрефакторить, должно приходить параметром
        return buildNode("IMG", {src: userConfig.cdnPath + "avatars/" + Sha256.hash(email) + ".png"}, null, {
            error: function () {
                this.error = null;
                this.src = "https://secure.gravatar.com/avatar/" + MD5(email) + "?d=mm";
            }
        });
    };
    this.initListeners = function(){
        addEvent(target, 'focus', function(){
            this.search();
        }.bind(this));
        addEvent(target, 'blur', function(){
            rmClass(this.target, 'js-active');
        }.bind(this));
        addEvent(target, 'keyup', this.handleKeyUps.bind(this));
        addEvent(target, 'keydown', this.handleKeyDowns.bind(this));

        addEvent(target, 'keypress', this.handleKeyPress.bind(this));
        addEvent(this.autocompleteWrap, 'mousedown', function(e){
            var autoCompleteTarget = e.target,
                tagName = autoCompleteTarget.tagName.toLowerCase();
            while(autoCompleteTarget != this.autocompleteWrap){
                if(autoCompleteTarget.tagName.toLocaleLowerCase() === 'div'){
                    break;
                }
                autoCompleteTarget = autoCompleteTarget.parentNode;
            }
            if(autoCompleteTarget.className != this.autocompleteWrap.className){
                stopBubble(e);
                this.setTargetValue(autoCompleteTarget.getAttribute('data-value'));
            }
            return false;
        }.bind(this));
        addEvent(this.autocompleteWrap, 'mouseover', function(e){
            var autoCompleteTarget = e.target,
                index = 1;
            while(autoCompleteTarget != this.autocompleteWrap){
                if(autoCompleteTarget.tagName.toLocaleLowerCase() === 'div'){
                    break;
                }
                autoCompleteTarget = autoCompleteTarget.parentNode;
            }
            while(autoCompleteTarget = autoCompleteTarget.previousElementSibling){
                ++index;
            }
            this.setCurrentPosition(index);
            this.moveActiveElement(true);
        }.bind(this));
    };
    this.setTargetValue = function(value){
        var string = this.target.value,
            arr,
            split = string.split(','),
            last = split[split.length - 1],
            index = string.indexOf(last, string.lastIndexOf(','));
        this.target.value  = (string.substring(0, index) + ' ' + value + ', ').replace(/^\s*/, '');
        arr = this.setSelectedValues();
        this.focusTarget();
        this.callbackSearch(arr);
        return arr.length ? arr : false; //returns array of inserted values or false
    };
    this.callbackSearch = function(arr){
        if(!window.isArray(arr) || !arr.length){
            arr = this.target.value.split(',').map(function(item){
                return item.replace(/^["\s]+|["\s]+$/g, '');
            });
        }
        userConfig.docFilter.docUser = arr.join('') ? arr : null;
        window.documentsCollection.offset = 0;
        window.documentsCollection.renderDocuments();
    };
    this.focusTarget = function(){
        /*FIXME setTimeout on focus is bad, imo*/
        this.search();
        setTimeout(function(){
            setCaretPosition(this.target, this.target.value.length);
            this.target.focus();
        }.bind(this), 0);
    };
    this.search = function(){
        var searchQuery = this.target.value.toLowerCase().split(','),
            last = searchQuery[searchQuery.length - 1].replace(/^["\s]+|["\s]+$/g, ''),
            i, length,
            temp;
        if(last){
            for(i = 0, length = this.mainArr.length; i < length; i++){
                if(this.keys){
                    temp = this.searchTroughKeys(this.mainArr[i], last);
                }else{
                    temp = this.mainArr[i].indexOf(last) != -1 && this.compareWithSelectedValues(this.mainArr[i]) ? this.mainArr[i] : false;
                }
                if(temp){
                    this.foundArr.push(temp);
                }
            }
            if(this.foundArr.length){
                this.autocompleteWrap.innerHTML = '';
                this.foundArrLength = this.foundArr.length;
                this.fillAutocompleteWrap(this.foundArr, this.autocompleteWrap, this.keys, last); /*will empty foundArr*/
                addClass(this.target, 'js-active');
                this.currentPosition = 1;
                addClass(this.autocompleteWrap.querySelector('div:nth-child('+this.currentPosition+')'), 'js-active');
            }else{
                rmClass(this.target, 'js-active');
            }
        }else{
            rmClass(this.target, 'js-active');
        }
    };
    this.searchTroughKeys = function(obj, comparator){
        comparator = comparator.toLowerCase();
        var temp,
            length,
            flag = false;
        for(temp = 0, length = this.searchKeys.length; temp < length; temp++){
            if(obj[this.searchKeys[temp]] && obj[this.searchKeys[temp]].toLowerCase().indexOf(comparator) != -1 && this.compareWithSelectedValues(obj)){
                flag = true;
                break;
            }
        }
        return flag ? obj : false;
    };
    this.setSelectedValues = function(){
        var arr = this.target.value.split(',').map(function(item){
                return item.replace(/^["\s]+|["\s]+$/g, '');
            }),
            length = arr.length,
            i,
            res = [];
        for(i = 0; i < length; ++i){ //_.compact
            if(arr[i]){
                res.push(arr[i]);
            }
        }
        this.selectedValues = res.length ? res : '';
        return res.length ? res : '';
    };
    this.compareWithSelectedValues = function(obj){
        var value = this.getValuesFromKeys(obj),
            selectedValues = this.selectedValues || [],
            i, length,
            res = true;
        for(i = 0, length = selectedValues.length; i < length; i++){
            if(this.selectedValues[i] && this.selectedValues[i].toLowerCase().indexOf(value) != -1){
                res = false;
                break;
            }
        }
        return res;
    };
    /*
     * Actual logic is here
     * */
    this.addWraps();
    this.initListeners();
    return this;
};