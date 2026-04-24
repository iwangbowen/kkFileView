function htmlttt(str) {
    var s = "";
    if (str.length == 0) return "";
    s = str.replace(/&amp;/gi, "&");
    s = s.replace(/&nbsp;/gi, " ");
    s = s.replace(/&#39;/gi, "\'");
    s = s.replace(/&quot;/gi, "\"");
    s = s.replace(/javascript/g, "javascript ");
    s = s.replace(/iframe/gi, "iframe ");
    return s;
}

function isEmptyString(str) {
    return !str || str.length === 0;
}

function removeExtraNewlines(str) {
    // 替换连续的换行符为单个换行符
    return str.replace(/(?:\r\n|\r|\n){2,}/g, '\n');
}

// 重命名构造函数为 Pagination 避免递归
function Pagination(content, kkkeyword, Length, page, txt) {
    if (page == 0) {
        page = 1;
    }
    
    // 存储实例属性
    this.content = content;
    this.contentLength = content.length;
    this.pageSizeCount = 0;
    this.perpageLength = Length;
    this.currentPage = page;
    this.kkkeyword = kkkeyword;
    this.txt = txt;
    this.regularExp = /\d+/;
    this.strDisplayContent = "";
    this.strDisplayPagenation = "";

    // 获取或创建分页容器
    this.divDisplayPagenation = document.getElementById("divPagenation");
    if (!this.divDisplayPagenation) {
        this.divDisplayPagenation = document.createElement("DIV");
        this.divDisplayPagenation.id = "divPagenation";
        document.body.appendChild(this.divDisplayPagenation);
    }

    // 获取或创建内容容器
    this.divDisplayContent = document.getElementById("divContent");
    if (!this.divDisplayContent) {
        this.divDisplayContent = document.createElement("DIV");
        this.divDisplayContent.id = "divContent";
        document.body.appendChild(this.divDisplayContent);
    }

    // 初始化
    this.initialize();
}

// 初始化分页
Pagination.prototype.initialize = function () {
    this.divDisplayContent.className = "divContent";

    if (this.contentLength <= this.perpageLength) {
        this.displayAllContent();
        return;
    }

    this.pageSizeCount = Math.ceil((this.contentLength / this.perpageLength));
    this.goto(this.currentPage);
};

// 显示所有内容（不分页）
Pagination.prototype.displayAllContent = function () {
    var content = this.content;
    
    if (this.txt == "code") {
        content = htmlttt(content);
        this.strDisplayContent = '<pre><code>' + content + '</code></pre>';
        this.divDisplayContent.innerHTML = this.strDisplayContent;
        if (!(!!window.ActiveXObject || "ActiveXObject" in window)) {
            if (typeof hljs !== 'undefined') {
                hljs.highlightAll(this.kkkeyword);
            }
        }
    } else if (this.txt == "js") {
        content = htmlttt(content);
        var result = '';
        if (typeof js_beautify !== 'undefined') {
            result = js_beautify(content, 1, "\t");
        } else {
            result = content;
        }
        this.strDisplayContent = '<pre><code class="language-js">' + result + '</code></pre>';
        this.divDisplayContent.innerHTML = this.strDisplayContent;
        if (!(!!window.ActiveXObject || "ActiveXObject" in window)) {
            if (typeof hljs !== 'undefined') {
                hljs.highlightAll(this.kkkeyword);
            }
        }
    } else {
        content = removeExtraNewlines(content);
        let list = content.split('\n');
        for (let i = 0; i < list.length; i++) {
            list[i] = i + "." + list[i];
        }
        let txtContent = list.join('\n');
        if (this.kkkeyword !== "" && this.kkkeyword !== null && this.kkkeyword !== "null") {
            txtContent = txtContent.split(this.kkkeyword).join("<span class='highlight'>" + this.kkkeyword + "</span>");
        }
        this.divDisplayContent.innerHTML = txtContent;
    }
};

// 显示分页栏
Pagination.prototype.displayPage = function () {
    this.strDisplayPagenation = "";
    
    if (this.currentPage && this.currentPage != 1) {
        this.strDisplayPagenation += '<button onclick="window.currentPagination.previous()">上一页</button> ';
    } else {
        this.strDisplayPagenation += "上一页 ";
    }

    if (this.currentPage && this.currentPage != this.pageSizeCount) {
        this.strDisplayPagenation += '<button onclick="window.currentPagination.next()">下一页</button> ';
        this.strDisplayPagenation += "<input type='number' value='" + this.currentPage + "' id='yemaPerpageLength' style='width:70px' /><button type='button' onclick='window.currentPagination.tiaozhuan()'>跳转</button> ";
    } else {
        this.strDisplayPagenation += "下一页 ";
    }
    
    if (isEmptyString(this.currentPage)) {
        this.currentPage = 1;
    }
    
    this.strDisplayPagenation += "第" + this.currentPage + "/" + this.pageSizeCount + "页。<br>每页 " + this.perpageLength + " 字符，调整字符数：<input type='number' value='" + this.perpageLength + "' id='ctlPerpageLength' style='width:70px' /><button onclick='window.currentPagination.change()'>确定</button>";
    this.divDisplayPagenation.innerHTML = this.strDisplayPagenation;
};

// 上一页
Pagination.prototype.previous = function () {
    this.goto(this.currentPage - 1);
};

// 下一页
Pagination.prototype.next = function () {
    this.goto(this.currentPage + 1);
};

// 跳转至某一页
Pagination.prototype.goto = function (iCurrentPage) {
    if (isEmptyString(iCurrentPage)) {
        iCurrentPage = 1;
    }
    
    if (this.regularExp.test(iCurrentPage)) {
        this.currentPage = parseInt(iCurrentPage);
        
        // 获取当前页的内容
        var startPos = (this.currentPage - 1) * this.perpageLength;
        var currentContent = this.content.substr(startPos, this.perpageLength);
        
        // 处理特殊分隔符（如果有）
        var indexOf = currentContent.indexOf("❈");
        if (indexOf >= 0) {
            var beginToEndContent = this.content.substr(0, this.currentPage * this.perpageLength);
            var reCount = beginToEndContent.split("❈").length - 1;
            var contentArray = currentContent.split("❈");
            currentContent = this.replaceStr(contentArray, reCount);
        }

        this.strDisplayContent = currentContent;
        this.displayPage();
        this.displayContent();
    } else {
        alert("页面参数错误");
    }
};

// 显示当前页内容
Pagination.prototype.displayContent = function () {
    var strDisplayContent = this.strDisplayContent;
    
    if (this.txt == "code") {
        strDisplayContent = htmlttt(strDisplayContent);
        strDisplayContent = "<pre><code>" + strDisplayContent + "</code></pre>";
        this.divDisplayContent.innerHTML = strDisplayContent;
        if (!(!!window.ActiveXObject || "ActiveXObject" in window)) {
            if (typeof hljs !== 'undefined') {
                hljs.highlightAll(this.kkkeyword);
            }
        }
    } else if (this.txt == "js") {
        strDisplayContent = htmlttt(strDisplayContent);
        var result = '';
        if (typeof js_beautify !== 'undefined') {
            result = js_beautify(strDisplayContent, 1, "\t");
        } else {
            result = strDisplayContent;
        }
        strDisplayContent = '<pre><code class="language-js">' + result + '</code></pre>';
        this.divDisplayContent.innerHTML = strDisplayContent;
        if (!(!!window.ActiveXObject || "ActiveXObject" in window)) {
            if (typeof hljs !== 'undefined') {
                hljs.highlightAll(this.kkkeyword);
            }
        }
    } else {
        if (this.kkkeyword !== "" && this.kkkeyword !== null && this.kkkeyword !== "null") {
            strDisplayContent = strDisplayContent.split(this.kkkeyword).join("<span class='highlight'>" + this.kkkeyword + "</span>");
        }
        this.divDisplayContent.innerHTML = strDisplayContent;
    }
};

// 改变每页的字节数
Pagination.prototype.change = function () {
    var iPerpageLength = document.getElementById("ctlPerpageLength").value;
    if (this.regularExp.test(iPerpageLength)) {
        // 创建新的分页实例
        window.currentPagination = new Pagination(
            this.content, 
            this.kkkeyword, 
            parseInt(iPerpageLength), 
            this.currentPage, 
            this.txt
        );
    } else {
        alert("请输入数字");
    }
};

// 跳转到指定页
Pagination.prototype.tiaozhuan = function () {
    var yema = document.getElementById("yemaPerpageLength").value;
    if (this.regularExp.test(yema)) {
        this.goto(yema);
    } else {
        alert("请输入数字");
    }
};

// 替换字符串函数
Pagination.prototype.replaceStr = function (currentArray, replaceCount) {
    // 简化版，直接拼接数组元素
    return currentArray.join('');
};

// 全局函数（保持向后兼容）
function DHTMLpagenation(content, kkkeyword, Length, page, txt) {
    // 创建 Pagination 实例
    window.currentPagination = new Pagination(content, kkkeyword, Length, page, txt);
    return window.currentPagination;
}