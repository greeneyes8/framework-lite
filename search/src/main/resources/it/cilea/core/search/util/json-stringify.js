JSONstring={compactOutput:false,includeProtos:false,includeFunctions:false,detectCirculars:true,restoreCirculars:true,make:function(e,t){this.restore=t;this.mem=[];this.pathMem=[];return this.toJsonStringArray(e).join("")},toObject:function(x){if(!this.cleaner){try{this.cleaner=new RegExp('^("(\\\\.|[^"\\\\\\n\\r])*?"|[,:{}\\[\\]0-9.\\-+Eaeflnr-u \\n\\r\\t])+?$')}catch(a){this.cleaner=/^(true|false|null|\[.*\]|\{.*\}|".*"|\d+|\d+\.\d+)$/}}if(!this.cleaner.test(x)){return{}}eval("this.myObj="+x);if(!this.restoreCirculars||!alert){return this.myObj}if(this.includeFunctions){var x=this.myObj;for(var i in x){if(typeof x[i]=="string"&&!x[i].indexOf("JSONincludedFunc:")){x[i]=x[i].substring(17);eval("x[i]="+x[i])}}}this.restoreCode=[];this.make(this.myObj,true);var r=this.restoreCode.join(";")+";";eval('r=r.replace(/\\W([0-9]{1,})(\\W)/g,"[$1]$2").replace(/\\.\\;/g,";")');eval(r);return this.myObj},toJsonStringArray:function(e,t){if(!t){this.path=[]}t=t||[];var n;switch(typeof e){case"object":this.lastObj=e;if(this.detectCirculars){var r=this.mem;var i=this.pathMem;for(var s=0;s<r.length;s++){if(e===r[s]){t.push('"JSONcircRef:'+i[s]+'"');return t}}r.push(e);i.push(this.path.join("."))}if(e){if(e.constructor==Array){t.push("[");for(var s=0;s<e.length;++s){this.path.push(s);if(s>0)t.push(",\n");this.toJsonStringArray(e[s],t);this.path.pop()}t.push("]");return t}else if(typeof e.toString!="undefined"){t.push("{");var o=true;for(var s in e){if(!this.includeProtos&&e[s]===e.constructor.prototype[s]){continue}this.path.push(s);var u=t.length;if(!o)t.push(this.compactOutput?",":",\n");this.toJsonStringArray(s,t);t.push(":");this.toJsonStringArray(e[s],t);if(t[t.length-1]==n)t.splice(u,t.length-u);else o=false;this.path.pop()}t.push("}");return t}return t}t.push("null");return t;case"unknown":case"undefined":case"function":if(!this.includeFunctions){t.push(n);return t}e="JSONincludedFunc:"+e;t.push('"');var a=["\\","\\\\","\n","\\n","\r","\\r",'"','\\"'];e+="";for(var s=0;s<8;s+=2){e=e.split(a[s]).join(a[s+1])}t.push(e);t.push('"');return t;case"string":if(this.restore&&e.indexOf("JSONcircRef:")==0){this.restoreCode.push("this.myObj."+this.path.join(".")+"="+e.split("JSONcircRef:").join("this.myObj."))}t.push('"');var a=["\n","\\n","\r","\\r",'"','\\"'];e+="";for(var s=0;s<6;s+=2){e=e.split(a[s]).join(a[s+1])}t.push(e);t.push('"');return t;default:t.push(String(e));return t}}}