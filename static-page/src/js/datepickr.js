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

var Datepickr = (function() {
	var datepickrs = [],
	currentDate = new Date(),
	date = {
		current: {
			year: function() {
				return currentDate.getFullYear();
			},
			month: {
				integer: function() {
					return currentDate.getMonth();
				},
				string: function(full, months) {
					var date = currentDate.getMonth();
					return monthToStr(date, full, months);
				}
			},
			day: function() {
				return currentDate.getDate();
			}
		},
		month: {
			string: function(full, currentMonthView, months) {
				return monthToStr(currentMonthView, full, months);
			},
			numDays: function(currentMonthView, currentYearView) {
				return (currentMonthView == 1 && !(currentYearView & 3) && (currentYearView % 100 || (currentYearView % 400==0))) ? 29 : daysInMonth[currentMonthView];
			}
		}
	},
	daysInMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31],
	buildCache = [],
	handlers = {
		calendarClick: function(e) {
			if(e.target.className) {
				switch(e.target.className) {
					case 'prev-year':
					case 'prevYear':
						this.currentYearView--;
						rebuildCalendar.call(this);
						break;
					case 'prev-month':
					case 'prevMonth':
						this.currentMonthView--;
						if(this.currentMonthView < 0) {
							this.currentYearView--;
							this.currentMonthView = 11;
						}
						rebuildCalendar.call(this);
						break;
					case 'next-month':
					case 'nextMonth':
						if (date.current.year()*100 + date.current.month.integer()>this.currentYearView*100+this.currentMonthView) {
							this.currentMonthView++;
							if (this.currentMonthView > 11) {
								this.currentYearView++;
								this.currentMonthView = 0;
							}
							rebuildCalendar.call(this);
						}
						break;
					case 'next-year':
					case 'nextYear':
						if (date.current.year()>this.currentYearView){
							this.currentYearView++;
							if (date.current.year()==this.currentYearView && this.currentMonthView>date.current.month.integer()){
								this.currentMonthView=date.current.month.integer();
							}
							rebuildCalendar.call(this);
						}
						break;
					case 'day': case 'clear':
						if (hasClass(e.target.parentNode,"no-click")){
							return;
						}
						if (e.target.className == 'clear'){
							this.element.value = "";
						} else {
                            if (this.element.hasOwnProperty("lastData")){
                            	if (this.element.lastData!=formatDate(new Date(this.currentYearView, this.currentMonthView, e.target.innerHTML).getTime(), this.config)){
                                    this.element.value = formatDate(new Date(this.currentYearView, this.currentMonthView, e.target.innerHTML).getTime(), this.config);
                                }
                            } else {
                                this.element.value = formatDate(new Date(this.currentYearView, this.currentMonthView, e.target.innerHTML).getTime(), this.config);
                            }
                            this.element.lastData=this.element.value;

						}
						rmClass(this.element,"errorField");						
						if (this.config.closeAfterSelect){
							this.close();
						}
						if (typeof this.element.onchange === 'function' ){
							this.element.onchange();
						}
						break;
					case 'month':
						if (this.config.additionalSelector){
							this.config.additionalSelector = false;
							this.config.daySelector = true;
							this.currentMonthView = this.config.months.indexOf(e.target.innerHTML);
							this.calendar.getElementsByTagName("TR")[0].style.display = "";
							var btns = this.calendar.getElementsByTagName("SPAN");
							for (var i in btns){
								if (btns.hasOwnProperty(i) && typeof btns[i].className !== 'undefined' && (btns[i].className == "prev-month" || hasClass(btns[i],"next-month"))){
									btns[i].style.display="";
								}
							}

							this.calendar.getElementsByTagName("TR")[0].style.display = "";
							buildHeader(this.config);
							rebuildCalendar.call(this);
						} else {
                            if (this.element.hasOwnProperty("lastData")){
                            	if (this.element.lastData!=this.currentYearView + " " + e.target.innerHTML) {
                                    this.element.value = this.currentYearView + " " + e.target.innerHTML;
                                }
                            } else {
                                this.element.value = this.currentYearView + " " + e.target.innerHTML;
                            }
                            this.element.lastData=this.element.value;

							this.close();
							if (typeof this.element.onchange === 'function') {
                                this.element.onchange();
							}
						}
						break;

					case 'select-month':
						if (!this.config.daySelector){
							return;
						}
						this.config.daySelector=false;
						this.config.additionalSelector = true;
						//buildCalendar().call(this.calendar);
						this.calendar.getElementsByTagName("TR")[0].style.display = "none";
						var btn = this.calendar.getElementsByTagName("SPAN");
						for (var j in btn){
							if (btn.hasOwnProperty(j) && typeof btn[j].className !== 'undefined' && (btn[j].className == "prev-month" || hasClass(btn[j],"next-month"))){
								btn[j].style.display="none";
							}
						}
						buildHeader(this.config);
						rebuildCalendar.call(this);
						break
				}
			}
		},
		documentClick: function(e) {
			if(e.target != this.element && e.target != this.calendar) {
				var parentNode = e.target.parentNode;
				/** @namespace this.calender */
				if(parentNode != this.calender) {
					while(parentNode != this.calendar) {
						parentNode = parentNode.parentNode;
						if(parentNode == null) {
							this.close();
							break;
						}
					}
				}
			}
		}
	};

	function formatDate(milliseconds, config) {
		var formattedDate = '',
		dateObj = new Date(milliseconds),
		format = {
			d: function() {
				var day = format.j();
				return (day < 10) ? '0' + day : day;
			},
			/**
			 * @return {string}
			 */
			D: function() {
				return config.weekdays[format.w()].substring(0, 3);
			},
			j: function() {
				return dateObj.getDate();
			},
			l: function() {
				return config.weekdays[format.w()];
			},
			S: function() {
				return config.suffix[format.j()] || config.defaultSuffix;
			},
			w: function() {
				return dateObj.getDay();
			},
			m: function() {
				var month = format.n() + 1;
				return (month < 10) ? '0' + month : month;
			},
			M: function() {
				return monthToStr(format.n(), false, config.months);
			},
			n: function() {
				return dateObj.getMonth();
			},
			/**
			 * @return {number}
			 */
			Y: function() {
				return dateObj.getFullYear();
			},
			y: function() {
				return format.Y().toString().substring(2, 4);
			}
		},
		formatPieces = config.dateFormat.split('');

		foreach(formatPieces, function(formatPiece, index) {
			if(format[formatPiece] && formatPieces[index - 1] != '\\') {
				formattedDate += format[formatPiece]();
			} else {
				if(formatPiece != '\\') {
					formattedDate += formatPiece;
				}
			}
		});

		return formattedDate;
	}

	function foreach(items, callback) {
		var x = items.length;
		for(var i=0; i < x; i++) {
			if(callback(items[i], i) === false) {
				break;
			}
		}
	}

	function addEvent(element, eventType, callback) {
		if(element.addEventListener) {
			element.addEventListener(eventType, callback, false);
		} else if(element.attachEvent) {
			var fixedCallback = function(e) {
				e = e || window.event;
				e.preventDefault = (function(e) {
					return function() { e.returnValue = false; };
				})(e);
				e.stopPropagation = (function(e) {
					return function() { e.cancelBubble = true; };
				})(e);
				e.target = e.srcElement;
				callback.call(element, e);
			};
			element.attachEvent('on' + eventType, fixedCallback);
		}
	}

	function removeEvent(element, eventType, callback) {
		if(element.removeEventListener) {
			element.removeEventListener(eventType, callback, false);
		} else if(element.detachEvent) {
			element.detachEvent('on' + eventType, callback);
		}
	}

	function buildNode(nodeName, attributes, content) {
		var element;

		if(!(nodeName in buildCache)) {
			buildCache[nodeName] = document.createElement(nodeName);
		}

		element = buildCache[nodeName].cloneNode(false);

		if(attributes != null) {
			for(var attribute in attributes) {
				if (attributes.hasOwnProperty(attribute)) {
					element[attribute] = attributes[attribute];
				}
			}
		}

		if(content != null) {
			if(typeof(content) == 'object') {
				element.appendChild(content);
			} else {
				element.innerHTML = content;
			}
		}

		return element;
	}

	function monthToStr(date, full, months) {
		return ((full == true) ? months[date] : ((months[date].length > 3) ? months[date].substring(0, 3) : months[date]));
	}

	function isToday(day, currentMonthView, currentYearView) {
		return day == date.current.day() && currentMonthView == date.current.month.integer() && currentYearView == date.current.year();
	}

	function buildWeekdays(weekdays) {
		var weekdayHtml = document.createDocumentFragment();
		foreach(weekdays, function(weekday) {
			weekdayHtml.appendChild(buildNode('th', {}, weekday.substring(0, 2)));
		});
		return weekdayHtml;
	}

	function rebuildCalendar() {
		while(this.calendarBody.hasChildNodes()){
			this.calendarBody.removeChild(this.calendarBody.lastChild);
		}

		var firstOfMonth = new Date(this.currentYearView, this.currentMonthView, 1).getDay()-1,
		numDays = date.month.numDays(this.currentMonthView, this.currentYearView);

		this.currentMonth.innerHTML =  '<span class="select-month">'+ this.currentYearView + '<br>' + date.month.string(this.config.fullCurrentMonth, this.currentMonthView, this.config.months)+'</span>';
        if (this.config.daySelector) {
			if (date.current.year() * 100 + date.current.month.integer() > this.currentYearView * 100 + this.currentMonthView) {
				rmClass(byClass(this.calendarBody.parentNode.parentNode, "next-month")[0], "no-click");
			} else {
				addClass(byClass(this.calendarBody.parentNode.parentNode, "next-month")[0], "no-click");
			}
		}
        if(date.current.year()>this.currentYearView){
            rmClass(byClass(this.calendarBody.parentNode.parentNode,"next-year")[0],"no-click");
        } else {
            addClass(byClass(this.calendarBody.parentNode.parentNode,"next-year")[0],"no-click");
        }
		if (this.config.daySelector){
			this.calendarBody.appendChild(buildDays(firstOfMonth, numDays, this.currentMonthView, this.currentYearView));
		} else {
			this.calendarBody.appendChild(buildMonths(this.currentMonthView, this.currentYearView, this.config.months, date.current.year()==this.currentYearView));
		}
	}

	function buildCurrentMonth(config, currentMonthView, currentYearView, months) {
		return buildNode('span', { className: 'current-month' }, '<span class="select-month">' + currentYearView + '<br>' + date.month.string(config.fullCurrentMonth, currentMonthView, months) +'</span>');
	}

	function buildHeader(config) {
		var months = buildNode('div', { className: 'months' });
		if(config.daySelector){
			months.appendChild(buildNode('span', { className: 'prev-year' }, buildNode('span', { className: 'prevYear', title:"Предидущий год" }, '≪')));
			months.appendChild(buildNode('span', { className: 'prev-month' }, buildNode('span', { className: 'prevMonth', title:"Предидущий месяц" }, '<')));
			months.appendChild(buildNode('span', { className: 'next-year' }, buildNode('span', { className: 'nextYear', title:"Следующий год" }, '≫')));
			months.appendChild(buildNode('span', { className: 'next-month'}, buildNode('span', { className: 'nextMonth', title:"Следующий месяц" }, '>')));
		} else {
			months.appendChild(buildNode('span', { className: 'prev-year' }, buildNode('span', { className: 'prevYear', title:"Предидущий год" }, '&nbsp;&nbsp;&nbsp;≪&nbsp;&nbsp;&nbsp;')));
			months.appendChild(buildNode('span', { className: 'next-year' }, buildNode('span', { className: 'nextYear', title:"Следующий год" }, '&nbsp;&nbsp;&nbsp;≫&nbsp;&nbsp;&nbsp;')));
		}
		return months;
	}

	function buildDays(firstOfMonth, numDays, currentMonthView, currentYearView) {
		var calendarBody = document.createDocumentFragment(),
		row = buildNode('tr'),
		dayCount = 0, i;
		if (firstOfMonth==-1){
			firstOfMonth=6;
		}
		/* print out previous month's "days" */
		for(i = 1; i <= firstOfMonth; i++) {
			row.appendChild(buildNode('td', null, '&nbsp;'));
			dayCount++;
		}

		for(i = 1; i <= numDays; i++) {
			/* if we have reached the end of a week, wrap to the next line */
			if(dayCount == 7) {
				calendarBody.appendChild(row);
				row = buildNode('tr');
				dayCount = 0;
			}

			var todayClassName = isToday(i, currentMonthView, currentYearView) ? { className: 'today' } : null;
			// detect future
			if (currentYearView*10000+currentMonthView*100+i > date.current.year()*10000 + date.current.month.integer()*100 + date.current.day()){
				todayClassName= {"className":"no-click"}
			}
			row.appendChild(buildNode('td', todayClassName, buildNode('span', { className: 'day' }, i)));
			dayCount++;
		}

		/* if we haven't finished at the end of the week, start writing out the "days" for the next month */
		for(i = 1; i <= (7 - dayCount); i++) {
			row.appendChild(buildNode('td', null, '&nbsp;'));
		}

		calendarBody.appendChild(row);

		row = buildNode('tr');
		row.appendChild(buildNode('td', {colSpan :"7"}, buildNode('span', { className: 'clear'}, "Видалити")));
		calendarBody.appendChild(row);

		return calendarBody;
	}

	function buildMonths(currentMonthView, currentYearView, months, currentYear) {

		var calendarBody = document.createDocumentFragment(),
			row = buildNode('tr');

		for(var i = 0; i <12; i++) {
			if( i%3 == 0 ) {
				calendarBody.appendChild(row);
				row = buildNode('tr');
			}
            if (currentYearView*100+i > date.current.year()*100 + date.current.month.integer()){
                row.appendChild(buildNode('td', {"className":"no-click"}, buildNode('span', { className: 'month' }, months[i])));
            } else {
                row.appendChild(buildNode('td', "", buildNode('span', { className: 'month' }, months[i])));
            }


		}
		calendarBody.appendChild(row);
		return calendarBody;
	}

	function buildCalendar() {
		var firstOfMonth = new Date(this.currentYearView, this.currentMonthView, 1).getDay()-1,
		numDays = date.month.numDays(this.currentMonthView, this.currentYearView),
		self = this;

		var inputLeft = 0;
		var inputTop = 0;
		var obj = this.element;

		if (typeof(this.holder)!=='undefined'){
			obj = this.holder;
		}

		if(obj.offsetParent) {
			do {
				inputLeft += obj.offsetLeft;
				inputTop += obj.offsetTop;
				obj = obj.offsetParent;
			} while (obj);
		}

		var calendarContainer = buildNode('div', { className: 'calendar' });
		if(typeof(this.holder)==='undefined'){
			calendarContainer.style.cssText = 'display: none; position: absolute; top: ' + (inputTop + this.element.offsetHeight) + 'px; left: ' + inputLeft + 'px; z-index: 100;';
		} else {
			calendarContainer.style.cssText = 'display: none; position: absolute; top: ' + (inputTop + this.holder.offsetHeight+1) + 'px; left: ' + inputLeft + 'px; z-index: 100;';
		}

		this.currentMonth = buildCurrentMonth(this.config, this.currentMonthView, this.currentYearView, this.config.months);
		var months = buildHeader(this.config);
		months.appendChild(this.currentMonth);

		var calendar = buildNode('table', null, this.config.daySelector? buildNode('thead', null, buildNode('tr', { className: 'weekdays' }, buildWeekdays(this.config.weekdays))):null);
		this.calendarBody = buildNode('tbody');
		if (this.config.daySelector){
			this.calendarBody.appendChild(buildDays(firstOfMonth, numDays, this.currentMonthView, this.currentYearView));
		} else {
			this.calendarBody.appendChild(buildMonths(this.currentMonthView, this.currentYearView, this.config.months, date.current.year()==this.currentYearView));
		}
		calendar.appendChild(this.calendarBody);

		calendarContainer.appendChild(months);
		calendarContainer.appendChild(calendar);

		document.body.appendChild(calendarContainer);

		addEvent(calendarContainer, 'click', function(e) {e.stopPropagation(); handlers.calendarClick.call(self, e); });
		if (this.config.daySelector) {
			if (date.current.year() * 100 + date.current.month.integer() > this.currentYearView * 100 + this.currentMonthView) {
				rmClass(byClass(this.calendarBody.parentNode.parentNode, "next-month")[0], "no-click");
			} else {
				addClass(byClass(this.calendarBody.parentNode.parentNode, "next-month")[0], "no-click");
			}
		}
        if(date.current.year()>this.currentYearView){
            rmClass(byClass(this.calendarBody.parentNode.parentNode,"next-year")[0],"no-click");
        } else {
            addClass(byClass(this.calendarBody.parentNode.parentNode,"next-year")[0],"no-click");
        }
		return calendarContainer;
	}

	function hasClass(el, name) {
		return new RegExp('(\\s|^)' + name + '(\\s|$)').test(el.className);
	}

	function addClass(el, name) {
		if (!hasClass(el, name)) {
			el.className += (el.className ? ' ' : '') + name;
		}
	}

	function rmClass(el, name) {
		if (hasClass(el, name)) {
			el.className = el.className.replace(name, "").trim();
		}
	}

	function fireEvent(obj, evt) {
		var fireOnThis = obj;
		var evObj;
		if (document.createEvent) {
			evObj = document.createEvent('MouseEvents');
			evObj.initEvent(evt, true, false);
			fireOnThis.dispatchEvent(evObj);

		} else if (document.createEventObject) {
			evObj = document.createEventObject();
			fireOnThis.fireEvent('on' + evt, evObj);
		}
	}

	function parseDate(date) {
		var parts = date.split('.');
		if (parts.length != 3 || parts[0].length > 2 || parts[1].length > 2 || parts[2].length != 4 || parseInt(parts[0], 10) > 31 || parseInt(parts[1], 10) > 12) {
			return;
		}
		var result = new Date(parts[2], parts[1] - 1, parts[0]);
		if (isNaN(result.getTime())) {
			return;
		}
		return result;
	}

	// Constructor
	return function(element, userConfig, curDate, holder, callback) {
		if (typeof element == "undefined" || element == null){
			return;
		}
		element.datapicker=this;
		element.onkeyup=function(){
			if (this.value==""){
				rmClass(this,"errorField");
				return;
			}
			var cd = parseDate(this.value);
			if (cd==null){
				addClass(this,"errorField");
			} else {
				rmClass(this,"errorField");
				this.datapicker.currentYearView = cd.getFullYear();
				this.datapicker.currentMonthView = cd.getMonth();
				rebuildCalendar.call(this.datapicker);
			}
		};

		var self = this;

		if (typeof element == "string"){
			this.element = byId(element);
		} else {
			this.element = element;
		}
		this.holder=holder;
		this.config = {
			additionalSelector:false,
			closeAfterSelect:true,
			daySelector:true,
			fullCurrentMonth: true,
			dateFormat: 'd.m.Y',
			weekdays: ['Пн','Вт','Ср','Чт','Пт','Сб','Нд'],
			months: ['Січень','Лютий','Березень','Квітень','Травень','Червень', 'Липень','Серпень','Вересень','Жовтень','Листопад','Грудень'],
			suffix: { 1: '' },
			defaultSuffix: ''
		};

		var cd;
		if (typeof(curDate)==='undefined'){
			cd=date.current;
		} else{
			cd=curDate;
		}

		this.currentYearView = cd.year();
		this.currentMonthView = cd.month.integer();

		if(userConfig) {
			for(var key in userConfig) {
				if(userConfig.hasOwnProperty(key) && this.config.hasOwnProperty(key)) {
					this.config[key] = userConfig[key];
				}
			}
		}

		this.documentClick = function(e) {handlers.documentClick.call(self, e); };

		this.open = function() {
			addEvent(document, 'click', self.documentClick);
			foreach(datepickrs, function(datepickr) {
				if(datepickr != self) {
					datepickr.close();
				}
			});
			//if (self.calendar.style.left == "0px" ){
				var inputLeft = 0;
				var inputTop = 0;
				var obj = self.element;

				if(obj.offsetParent) {
					do {
						inputLeft += obj.offsetLeft;
						inputTop += obj.offsetTop;
						obj = obj.offsetParent;
					} while (obj);
				}
				if(typeof(self.holder)==='undefined'){
					self.calendar.style.top = (inputTop + self.element.offsetHeight) + 'px';
				} else {
					self.calendar.style.top = (inputTop + self.element.offsetHeight+1) + 'px';
				}

				var viewportWidth = document.documentElement.clientWidth;
				if((inputLeft+200)>viewportWidth){
					self.calendar.style.left="";
					self.calendar.style.right = (viewportWidth - (inputLeft + self.element.offsetWidth))-2 + 'px';
				} else {
					self.calendar.style.left = inputLeft + 'px';
				}
			//}
			self.calendar.style.display = 'block';
		};

		this.close = function() {
			removeEvent(document, 'click', self.documentClick);
			fireEvent(self.element, "change");
			self.calendar.style.display = 'none';
		};

		this.calendar = buildCalendar.call(this);

		var pushFlag=true;
		for (var e in datepickrs){
			if (datepickrs.hasOwnProperty(e)) {
				if (datepickrs[e].element == this.element) {
					datepickrs[e] = this;
					datepickrs[e].calendar.parentNode.removeChild(datepickrs[e].calendar);
					pushFlag = false;
					break;
				}
			}
		}
		if (pushFlag){
			datepickrs.push(this);
		}

		if(this.element.nodeName == 'INPUT') {
			addEvent(this.element, 'focus', this.open);
		} else {
			addEvent(this.element, 'click', this.open);
		}

		if (!this.config.daySelector) {
            if (this.element.hasOwnProperty("lastData")){
            	if (this.element.lastData!=this.currentYearView + " " + this.config.months[cd.month.integer()]) {
                    this.element.value = this.currentYearView + " " + this.config.months[cd.month.integer()];
                }
            } else {
                this.element.value = this.currentYearView + " " + this.config.months[cd.month.integer()];
            }
            this.element.lastData=this.element.value;
		}
	};
})();
