
function isNotEmpty(value) {
  return value !== null && value !== undefined && value !== '' && value !== 'false' ;
}

function watermarkObj(watermarkContainer,watermarkTxt) {
try {
if (!isNotEmpty(watermarkTxt)) {
    return ;
}
		var watermarkSettings = {
			watermark_txt: watermarkTxt,
			watermark_start_x:80,//水印起始位置x轴坐标
			watermark_start_y:80,//水印起始位置Y轴坐标
			watermark_x_space:80,//水印x轴间隔
			watermark_y_space:80,//水印y轴间隔
			watermark_color:'black',//水印字体颜色
			watermark_alpha:0.2,//水印透明度
			watermark_fontsize:'18px',//水印字体大小
			watermark_font:'微软雅黑',//水印字体
			watermark_width:200,//水印宽度
			watermark_height:80,//水印高度
			watermark_angle:30//水印倾斜度数
		};
	//	console.log(watermarkContainer);
		var page_width = $(watermarkContainer).width() - watermarkSettings.watermark_width;
		var page_height = $(watermarkContainer).height() - watermarkSettings.watermark_height;
        page_width = (page_width < 250) ? 250 : page_width;
        page_height = (page_height < 250) ? 250 : page_height;
		var oTemp = document.createDocumentFragment();
		for (var x = watermarkSettings.watermark_start_x; x < page_width; x+= watermarkSettings.watermark_x_space) {
			for (var y = watermarkSettings.watermark_start_y; y < page_height; y+= watermarkSettings.watermark_y_space) {
				var mask_div = document.createElement('div');
				// mask_div.id = 'mask_div' + x + y;
				mask_div.className = 'mask_div';
				mask_div.appendChild(document.createTextNode(watermarkTxt));
				// 设置水印div倾斜显示
				mask_div.style.filter = "progid:DXImageTransform.Microsoft.Alpha(opacity="+(watermarkSettings.watermark_alpha*100)+")";
				mask_div.style.webkitTransform = "rotate(-" + watermarkSettings.watermark_angle + "deg)";
				mask_div.style.MozTransform = "rotate(-" + watermarkSettings.watermark_angle + "deg)";
				mask_div.style.msTransform = "rotate(-" + watermarkSettings.watermark_angle + "deg)";
				mask_div.style.OTransform = "rotate(-" + watermarkSettings.watermark_angle + "deg)";
				mask_div.style.transform = "rotate(-" + watermarkSettings.watermark_angle + "deg)";
				mask_div.style.visibility = "";
				mask_div.style.position = "absolute";
				mask_div.style.left = x + 'px';
				mask_div.style.top = y + 'px';
				mask_div.style.overflow = "hidden";
				mask_div.style.zIndex = "100";
				mask_div.style.pointerEvents='none';//pointer-events:none  让水印不遮挡页面的点击事件
				//mask_div.style.border="solid #eee 1px";
				mask_div.style.opacity = watermarkSettings.watermark_alpha;
				mask_div.style.fontSize = watermarkSettings.watermark_fontsize;
				mask_div.style.fontFamily = watermarkSettings.watermark_font;
				mask_div.style.color = watermarkSettings.watermark_color;
				mask_div.style.textAlign = "center";
				mask_div.style.width = watermarkSettings.watermark_width + 'px';
				mask_div.style.height = watermarkSettings.watermark_height + 'px';
				mask_div.style.display = "block";
				oTemp.appendChild(mask_div);
			}
		}
        $(watermarkContainer).append(oTemp);
	} catch (e) {
	console.log(e);
	}
	
}

