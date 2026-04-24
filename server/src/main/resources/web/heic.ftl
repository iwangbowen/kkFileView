<!DOCTYPE html>
<html lang="en">
<head>
<title>${file.name}文件预览</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, user-scalable=yes, initial-scale=1.0">
    <#include "*/commonHeader.ftl">
   <script src="js/base64.min.js" type="text/javascript"></script>
   <script src="/heic/src/index.js" type="text/javascript"></script>
   <style>
     :root {
        --primary-bg: #1a1a1a;
        --secondary-bg: #2d2d2d;
        --card-bg: #ffffff;
        --border-color: #404040;
        --shadow-light: rgba(0, 0, 0, 0.1);
        --shadow-medium: rgba(0, 0, 0, 0.2);
        --accent-color: #4a90e2;
        --text-primary: #333333;
        --text-secondary: #666666;
        --radius-sm: 8px;
        --radius-md: 12px;
        --radius-lg: 16px;
        --transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
    
    * {
        margin: 0;
        padding: 0;
        box-sizing: border-box;
    }
    
    body {
        background: linear-gradient(135deg, var(--primary-bg) 0%, var(--secondary-bg) 100%);
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
        min-height: 100vh;
        padding: 20px;
        display: flex;
        justify-content: center;
        align-items: flex-start;
    }
    
    .container {
        max-width: 1400px;
        width: 100%;
        margin: 0 auto;
        padding: 20px;
    }
    
    .gallery-header {
        text-align: center;
        margin-bottom: 30px;
        padding-bottom: 20px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }
    
    .gallery-title {
        color: white;
        font-size: 2rem;
        font-weight: 600;
        margin-bottom: 8px;
        text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
    }
    
    .file-meta {
        color: rgba(255, 255, 255, 0.7);
        font-size: 0.95rem;
        letter-spacing: 0.5px;
    }
    
    /* 单张图片时的居中展示样式 */
    .single-image-container {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 70vh;
        margin: 20px 0;
    }
    
    .single-image-wrapper {
        position: relative;
        max-width: 90%;
        max-height: 80vh;
        background: var(--card-bg);
        border-radius: var(--radius-md);
        overflow: hidden;
        box-shadow: 0 10px 40px rgba(0, 0, 0, 0.4);
        transition: var(--transition);
        cursor: pointer;
    }
    
    .single-image-wrapper:hover {
        transform: translateY(-5px);
        box-shadow: 0 15px 50px rgba(0, 0, 0, 0.5);
    }
    
    .single-photo {
        width: 100%;
        height: auto;
        display: block;
        max-height: 80vh;
        object-fit: contain;
    }
    
    .gallery-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
        gap: 30px;
        align-items: start;
        justify-items: center;
    }
    
    .photo-wrapper {
        position: relative;
        width: 100%;
        max-width: 500px;
        background: var(--card-bg);
        border-radius: var(--radius-md);
        overflow: hidden;
        box-shadow: 0 6px 25px rgba(0, 0, 0, 0.3);
        transition: var(--transition);
        cursor: pointer;
    }
    
    .photo-wrapper:hover {
        transform: translateY(-10px) scale(1.02);
        box-shadow: 0 15px 40px rgba(0, 0, 0, 0.4);
    }
    
    .photo-wrapper:hover .photo-overlay {
        opacity: 1;
        transform: translateY(0);
    }
    
    .my-photo {
        width: 100%;
        height: auto;
        display: block;
        transition: var(--transition);
        aspect-ratio: 4/3;
        object-fit: contain;
        background: #f8f9fa;
        padding: 10px;
    }
    
    .photo-overlay {
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        background: linear-gradient(transparent, rgba(0, 0, 0, 0.9));
        color: white;
        padding: 20px 15px 15px;
        opacity: 0;
        transform: translateY(10px);
        transition: var(--transition);
    }
    
    .photo-index {
        font-size: 0.9rem;
        color: rgba(255, 255, 255, 0.95);
        font-weight: 600;
        text-align: center;
    }
    
    .photo-loading {
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        width: 50px;
        height: 50px;
        border: 4px solid rgba(255, 255, 255, 0.3);
        border-top-color: var(--accent-color);
        border-radius: 50%;
        animation: photo-spin 1s linear infinite;
        opacity: 0;
        transition: opacity 0.3s;
    }
    
    .photo-loading.active {
        opacity: 1;
    }
    
    @keyframes photo-spin {
        to { transform: translate(-50%, -50%) rotate(360deg); }
    }
    
    .photo-placeholder {
        width: 100%;
        height: 300px;
        background: linear-gradient(45deg, #f5f5f5 25%, #e8e8e8 25%, #e8e8e8 50%, #f5f5f5 50%, #f5f5f5 75%, #e8e8e8 75%);
        background-size: 40px 40px;
        animation: placeholder-shimmer 2s linear infinite;
        border-radius: var(--radius-md);
    }
    
    @keyframes placeholder-shimmer {
        0% { background-position: -40px 0; }
        100% { background-position: 40px 0; }
    }
    
    .photo-error {
        position: relative;
        width: 100%;
        height: 300px;
        background: #f8f9fa;
        border: 2px dashed #dc3545;
        border-radius: var(--radius-md);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        color: #dc3545;
        padding: 20px;
    }
    
    .photo-error::before {
        content: "⚠️";
        font-size: 2.5rem;
        margin-bottom: 15px;
    }
    
    .photo-error-message {
        font-size: 0.9rem;
        text-align: center;
        margin-top: 10px;
        color: var(--text-secondary);
    }
    
    .fullscreen-modal {
        display: none;
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.98);
        z-index: 1000;
        align-items: center;
        justify-content: center;
        opacity: 0;
        transition: opacity 0.3s ease;
    }
    
    .fullscreen-modal.active {
        display: flex;
        opacity: 1;
    }
    
    .fullscreen-image {
        max-width: 95%;
        max-height: 95%;
        object-fit: contain;
        border-radius: var(--radius-sm);
        box-shadow: 0 15px 50px rgba(0, 0, 0, 0.8);
        animation: modal-fade-in 0.3s ease-out;
    }
    
    @keyframes modal-fade-in {
        from { opacity: 0; transform: scale(0.95); }
        to { opacity: 1; transform: scale(1); }
    }
    
    .close-button {
        position: absolute;
        top: 25px;
        right: 25px;
        width: 60px;
        height: 60px;
        background: rgba(255, 255, 255, 0.15);
        border: none;
        border-radius: 50%;
        color: white;
        font-size: 1.8rem;
        cursor: pointer;
        transition: var(--transition);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1001;
    }
    
    .close-button:hover {
        background: rgba(255, 255, 255, 0.25);
        transform: rotate(90deg) scale(1.1);
    }
    
    .navigation-button {
        position: absolute;
        top: 50%;
        transform: translateY(-50%);
        width: 70px;
        height: 70px;
        background: rgba(255, 255, 255, 0.15);
        border: none;
        border-radius: 50%;
        color: white;
        font-size: 1.5rem;
        cursor: pointer;
        transition: var(--transition);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1001;
    }
    
    .navigation-button:hover {
        background: rgba(255, 255, 255, 0.25);
        transform: translateY(-50%) scale(1.1);
    }
    
    .prev-button {
        left: 30px;
    }
    
    .next-button {
        right: 30px;
    }
    
    .image-counter {
        position: absolute;
        bottom: 30px;
        left: 50%;
        transform: translateX(-50%);
        background: rgba(0, 0, 0, 0.8);
        color: white;
        padding: 12px 24px;
        border-radius: 25px;
        font-size: 1rem;
        font-weight: 500;
        box-shadow: 0 4px 15px rgba(0, 0, 0, 0.3);
    }
    
    .watermark {
        pointer-events: none;
    }
    
    /* 响应式设计 */
    @media (max-width: 1200px) {
        .gallery-grid {
            grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
            gap: 25px;
        }
        
        .container {
            padding: 15px;
        }
    }
    
    @media (max-width: 768px) {
        .gallery-grid {
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 20px;
        }
        
        .gallery-title {
            font-size: 1.5rem;
        }
        
        .fullscreen-image {
            max-width: 98%;
            max-height: 98%;
        }
        
        .navigation-button {
            width: 50px;
            height: 50px;
            font-size: 1.2rem;
        }
        
        .close-button {
            width: 50px;
            height: 50px;
            font-size: 1.5rem;
        }
        
        .single-image-wrapper {
            max-width: 95%;
        }
    }
    
    @media (max-width: 480px) {
        .gallery-grid {
            grid-template-columns: 1fr;
        }
        
        body {
            padding: 10px;
        }
        
        .container {
            padding: 10px;
        }
        
        .photo-wrapper:hover {
            transform: translateY(-5px) scale(1.01);
        }
        
        .photo-wrapper {
            max-width: 100%;
        }
    }
    
    /* 滚动条美化 */
    ::-webkit-scrollbar {
        width: 10px;
    }
    
    ::-webkit-scrollbar-track {
        background: var(--secondary-bg);
    }
    
    ::-webkit-scrollbar-thumb {
        background: rgba(255, 255, 255, 0.2);
        border-radius: 5px;
    }
    
    ::-webkit-scrollbar-thumb:hover {
        background: rgba(255, 255, 255, 0.3);
    }
    
    /* 图片缩放提示 */
    .zoom-hint {
        position: fixed;
        bottom: 20px;
        right: 20px;
        background: rgba(0, 0, 0, 0.7);
        color: white;
        padding: 10px 15px;
        border-radius: 20px;
        font-size: 0.85rem;
        opacity: 0;
        transform: translateY(10px);
        transition: all 0.3s ease;
        z-index: 999;
    }
    
    .zoom-hint.show {
        opacity: 1;
        transform: translateY(0);
    }
    </style>
</head>
<body>
<div class="container">
    <div class="gallery-header">
        <h1 class="gallery-title">📸 图片预览</h1>
        <div class="file-meta">${file.name} · 
            <#if imgUrls?is_sequence>${imgUrls?size}<#else>1</#if>张图片
        </div>
    </div>
    
    <#-- 处理imgUrls变量，确保它始终是一个列表 -->
    <#assign imageList = []>
    <#if imgUrls?is_string>
        <#assign imageList = [imgUrls]>
    <#elseif imgUrls?is_sequence>
        <#assign imageList = imgUrls>
    </#if>
    
    <#if imageList?size == 1>
    <#-- 单张图片时的居中展示 -->
    <#assign img = imageList[0]>
    <#if img?contains("http://") || img?contains("https://")|| img?contains("ftp://")|| img?contains("file://")>
        <#assign finalUrl="${img}">
    <#else>
        <#assign finalUrl="${baseUrl}${img}">
    </#if>
    
    <div class="single-image-container">
        <div class="single-image-wrapper" data-index="0">
            <div class="photo-loading"></div>
            <img class="single-photo" 
                 src="${finalUrl}" 
                 data-original="${finalUrl}"
                 alt="${file.name}"
                 loading="lazy">
            <div class="photo-overlay">
                <div class="photo-index">${file.name}</div>
            </div>
        </div>
    </div>
    <#else>
    <#-- 多张图片时的网格展示 -->
    <div class="gallery-grid">
        <#list imageList as img>
            <#if img?contains("http://") || img?contains("https://")|| img?contains("ftp://")|| img?contains("file://")>
                <#assign finalUrl="${img}">
            <#else>
                <#assign finalUrl="${baseUrl}${img}">
            </#if>
            
            <div class="photo-wrapper" data-index="${img?index}">
                <div class="photo-loading"></div>
                <img class="my-photo" 
                     src="${finalUrl}" 
                     data-original="${finalUrl}"
                     alt="图片 ${img?index + 1}"
                     loading="lazy">
                <div class="photo-overlay">
                    <div class="photo-index">图片 ${img?index + 1}</div>
                </div>
            </div>
        </#list>
    </div>
    </#if>
</div>

<!-- 缩放提示 -->
<div class="zoom-hint" id="zoomHint">点击图片可全屏查看</div>

<!-- 全屏查看模态框 -->
<div class="fullscreen-modal" id="fullscreenModal">
    <button class="close-button" onclick="closeFullscreen()">✕</button>
    <button class="navigation-button prev-button" onclick="navigateImage(-1)">❮</button>
    <button class="navigation-button next-button" onclick="navigateImage(1)">❯</button>
    <img class="fullscreen-image" id="fullscreenImage" src="" alt="">
    <div class="image-counter" id="imageCounter">1 / 
        <#if imgUrls?is_sequence>${imgUrls?size}<#else>1</#if>
    </div>
</div>

<script type="text/javascript">
    // 计算图片总数
    function getTotalImages() {
        <#if imgUrls?is_sequence>
            return ${imgUrls?size};
        <#else>
            return 1;
        </#if>
    }
    
    // 安全的DOM元素查找
    function safeQuerySelector(element, selector) {
        if (!element) return null;
        return element.querySelector(selector);
    }
    
    // 页面初始化
    document.addEventListener('DOMContentLoaded', function() {
        console.log('页面加载完成，开始初始化...');
        
        try {
            // 显示缩放提示
            showZoomHint();
            
            // 处理跨域图片
            processImageUrls();
            
            // 初始化图片交互
            initImageInteractions();
            
            // 设置HEIC转换监听器
            initHeicConverter();
            
            // 初始化水印
            initWatermark();
            
            console.log('页面初始化完成');
        } catch (error) {
            console.error('页面初始化出错:', error);
        }
    });
    
    // 显示缩放提示
    function showZoomHint() {
        const hint = document.getElementById('zoomHint');
        if (hint) {
            setTimeout(() => {
                hint.classList.add('show');
                setTimeout(() => {
                    hint.classList.remove('show');
                }, 3000);
            }, 1000);
        }
    }
    
    // 图片交互功能
    function initImageInteractions() {
        const photoWrappers = document.querySelectorAll('.photo-wrapper, .single-image-wrapper');
        console.log('找到', photoWrappers.length, '个图片容器');
        
        photoWrappers.forEach(wrapper => {
            const img = wrapper.querySelector('.my-photo') || wrapper.querySelector('.single-photo');
            if (!img) return;
            
            // 显示加载动画
            const loadingElement = safeQuerySelector(wrapper, '.photo-loading');
            
            // 检查图片是否已经加载完成
            if (img.complete) {
                if (loadingElement) {
                    loadingElement.classList.remove('active');
                }
            } else {
                // 图片正在加载，显示加载动画
                if (loadingElement) {
                    loadingElement.classList.add('active');
                }
                
                // 绑定load事件
                img.addEventListener('load', function() {
                    console.log('图片加载完成:', this.src);
                    const parent = this.closest('.photo-wrapper, .single-image-wrapper');
                    if (parent) {
                        const loading = parent.querySelector('.photo-loading');
                        if (loading) {
                            loading.classList.remove('active');
                        }
                    }
                });
                
                // 绑定error事件
                img.addEventListener('error', function() {
                    console.warn('图片加载失败:', this.src);
                    const parent = this.closest('.photo-wrapper, .single-image-wrapper');
                    if (parent) {
                        const loading = parent.querySelector('.photo-loading');
                        if (loading) {
                            loading.classList.remove('active');
                        }
                    }
                });
            }
            
            // 点击图片打开全屏
            wrapper.addEventListener('click', function() {
                if (img) {
                    openFullscreen(img);
                }
            });
            
            // 添加鼠标悬停放大效果
            wrapper.addEventListener('mouseenter', function() {
                if (img) {
                    img.style.transform = 'scale(1.05)';
                }
            });
            
            wrapper.addEventListener('mouseleave', function() {
                if (img) {
                    img.style.transform = 'scale(1)';
                }
            });
        });
    }
    
    // 跨域处理
    function processImageUrls() {
        var kkagent = '${kkagent}';
        var baseUrl = '${baseUrl}'.endsWith('/') ? '${baseUrl}' : '${baseUrl}' + '/';
        var kkkey = '${kkkey}';
        var images = document.querySelectorAll('.my-photo, .single-photo');
        images.forEach(function(img) {
            if (!img) return;
            var originalUrl = img.getAttribute('data-original');
            // 检查是否需要反代
            if (kkagent === 'true'|| !originalUrl.startsWith(baseUrl)) {
                // 构建反代URL
                var proxyUrl = baseUrl + 'getCorsFile?urlPath=' + encodeURIComponent(Base64.encode(originalUrl)) + "&key=" + kkkey;
                
                // 如果当前src不是反代URL，则更新
                if (img.src !== proxyUrl) {
                    img.src = proxyUrl;
                }
            }
        });
    }
    
    // HEIC转换器
    function initHeicConverter() {
        document.querySelectorAll('.my-photo, .single-photo').forEach(img => {
            if (!img) return;
            
            img.addEventListener('error', async function() {
                if (!this) return;
                
                this.title = this.alt || '图片加载失败';
                try {
                    if (typeof document.ConvertHeicToPng === 'function') {
                        this.src = await document.ConvertHeicToPng(this.src, stat => {
                            if (this) {
                                this.alt = stat;
                            }
                        });
                    }
                } catch (error) {
                    console.error('HEIC转换失败:', error);
                }
            });
        });
    }
    
    // 全屏查看功能
    let currentImageIndex = 0;
    const totalImages = getTotalImages();
    
    function openFullscreen(imgElement) {
        if (!imgElement) return;
        
        const wrapper = imgElement.closest('.photo-wrapper, .single-image-wrapper');
        if (!wrapper) return;
        
        const indexAttr = wrapper.getAttribute('data-index');
        currentImageIndex = indexAttr ? parseInt(indexAttr) : 0;
        
        const fullscreenImage = document.getElementById('fullscreenImage');
        const imageCounter = document.getElementById('imageCounter');
        
        if (fullscreenImage) {
            fullscreenImage.src = imgElement.src;
            fullscreenImage.alt = imgElement.alt || '全屏预览';
        }
        
        if (imageCounter) {
            imageCounter.textContent = `${currentImageIndex + 1} / ${totalImages}`;
        }
        
        const modal = document.getElementById('fullscreenModal');
        if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }
    
    function closeFullscreen() {
        const modal = document.getElementById('fullscreenModal');
        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = 'auto';
        }
    }
    
    function navigateImage(direction) {
        currentImageIndex += direction;
        
        if (currentImageIndex < 0) {
            currentImageIndex = totalImages - 1;
        } else if (currentImageIndex >= totalImages) {
            currentImageIndex = 0;
        }
        
        const targetImage = document.querySelector(`[data-index="${currentImageIndex}"] .my-photo, [data-index="${currentImageIndex}"] .single-photo`);
        const fullscreenImage = document.getElementById('fullscreenImage');
        const imageCounter = document.getElementById('imageCounter');
        
        if (targetImage && fullscreenImage) {
            fullscreenImage.src = targetImage.src;
            fullscreenImage.alt = targetImage.alt || '全屏预览';
        }
        
        if (imageCounter) {
            imageCounter.textContent = `${currentImageIndex + 1} / ${totalImages}`;
        }
    }
    
    // 键盘控制
    document.addEventListener('keydown', function(e) {
        const modal = document.getElementById('fullscreenModal');
        
        if (modal && modal.classList.contains('active')) {
            if (e.key === 'Escape') {
                closeFullscreen();
            } else if (e.key === 'ArrowLeft') {
                navigateImage(-1);
            } else if (e.key === 'ArrowRight') {
                navigateImage(1);
            } else if (e.key === '+' || e.key === '=') {
                // 放大图片
                const img = document.getElementById('fullscreenImage');
                if (img) {
                    const currentScale = img.style.transform ? parseFloat(img.style.transform.replace('scale(', '').replace(')', '')) || 1 : 1;
                    const newScale = Math.min(currentScale + 0.1, 3);
                    img.style.transform = 'scale(' + newScale + ')';
                }
            } else if (e.key === '-' || e.key === '_') {
                // 缩小图片
                const img = document.getElementById('fullscreenImage');
                if (img) {
                    const currentScale = img.style.transform ? parseFloat(img.style.transform.replace('scale(', '').replace(')', '')) || 1 : 1;
                    const newScale = Math.max(currentScale - 0.1, 0.5);
                    img.style.transform = 'scale(' + newScale + ')';
                }
            } else if (e.key === '0') {
                // 重置缩放
                const img = document.getElementById('fullscreenImage');
                if (img) {
                    img.style.transform = 'scale(1)';
                }
            }
        }
    });
    
    // 水印初始化
    function initWatermark() {
        if (!!window.ActiveXObject || "ActiveXObject" in window) {
            // IE浏览器不添加水印
            console.log('IE浏览器，跳过水印初始化');
        } else {
            if (typeof initWaterMark === 'function') {
                try {
                    initWaterMark();
                    console.log('水印初始化成功');
                } catch (error) {
                    console.error('水印初始化失败:', error);
                }
            } else {
                console.warn('initWaterMark函数未定义');
            }
        }
    }
    
    // 防止模态框点击事件冒泡
    document.getElementById('fullscreenModal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeFullscreen();
        }
    });
    
    // 添加图片缩放功能
    document.getElementById('fullscreenModal')?.addEventListener('wheel', function(e) {
        if (!this.classList.contains('active')) return;
        
        e.preventDefault();
        const img = document.getElementById('fullscreenImage');
        if (!img) return;
        
        const currentScale = img.style.transform ? parseFloat(img.style.transform.replace('scale(', '').replace(')', '')) || 1 : 1;
        const delta = e.deltaY > 0 ? -0.1 : 0.1;
        const newScale = Math.max(0.5, Math.min(currentScale + delta, 3));
        img.style.transform = 'scale(' + newScale + ')';
        img.style.transition = 'transform 0.1s ease';
    });
</script>
</body>
</html>