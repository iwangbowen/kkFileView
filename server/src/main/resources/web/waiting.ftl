<!DOCTYPE html>

<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">

  <title>${fileName}文件转换中</title>
  <style>
:root{--primary:#3498db;--primary-dark:#2980b9;--secondary:#2c3e50;--light:#ecf0f1;--warning:#f39c12;--gray:#95a5a6;--shadow:0 10px 30px rgba(0,0,0,0.1);--radius:12px;--transition:all 0.3s ease;}*{margin:0;padding:0;box-sizing:border-box;font-family:'Segoe UI','Microsoft YaHei',sans-serif;}
  body{background:linear-gradient(135deg,#f5f7fa 0%,#c3cfe2 100%);min-height:100vh;display:flex;justify-content:center;align-items:center;padding:20px;color:var(--secondary);}.container{max-width:600px;width:100%;background-color:white;border-radius:var(--radius);box-shadow:var(--shadow);overflow:hidden;padding:40px;text-align:center;animation:fadeIn 0.8s ease-out;}@keyframes fadeIn{from{opacity:0;transform:translateY(20px);}
  to{opacity:1;transform:translateY(0);}}.header{margin-bottom:30px;}.header h1{color:var(--secondary);font-size:28px;margin-bottom:10px;}.subtitle{color:var(--gray);font-size:16px;}.spinner{border:8px solid rgba(52,152,219,0.1);border-top:8px solid var(--primary);border-radius:50%;width:80px;height:80px;animation:spin 1.5s linear infinite;margin:0 auto 30px;}@keyframes spin{0%{transform:rotate(0deg);}
  100%{transform:rotate(360deg);}}.file-info{background-color:#f8f9fa;border-radius:var(--radius);padding:20px;margin-bottom:30px;text-align:left;border-left:4px solid var(--primary);}.file-info h3{margin-bottom:10px;color:var(--secondary);}.file-name{font-weight:bold;color:var(--primary);word-break:break-all;}.message{font-size:18px;margin-bottom:25px;color:var(--secondary);padding:15px;background-color:#f8f9fa;border-radius:var(--radius);line-height:1.5;}.countdown-section{margin:30px 0;padding:20px;background:linear-gradient(to right,#f8f9fa,#e9ecef);border-radius:var(--radius);}.countdown-text{font-size:16px;margin-bottom:10px;}#countdown{font-weight:bold;font-size:28px;color:var(--primary);display:inline-block;min-width:40px;}.controls{display:flex;justify-content:center;gap:20px;margin-top:30px;flex-wrap:wrap;}.btn{padding:14px 28px;border:none;border-radius:50px;font-weight:600;font-size:16px;cursor:pointer;transition:var(--transition);min-width:180px;}.btn-primary{background-color:var(--primary);color:white;}.btn-primary:hover{background-color:var(--primary-dark);}.btn-secondary{background-color:var(--light);color:var(--secondary);border:2px solid var(--gray);}.btn-secondary:hover{background-color:#e0e0e0;}.footer{margin-top:40px;color:var(--gray);font-size:14px;padding-top:20px;border-top:1px solid#eee;}.tips{background-color:#fff8e1;border-radius:var(--radius);padding:15px;margin-top:25px;font-size:14px;text-align:left;border-left:4px solid var(--warning);}.tips h4{margin-bottom:8px;color:var(--secondary);}.tips ul{padding-left:20px;margin-bottom:0;}.tips li{margin-bottom:5px;}@media(max-width:576px){.container{padding:25px 20px;}.header h1{font-size:24px;}.btn{min-width:100%;}.controls{flex-direction:column;}}
  </style>
</head>

<body>
  <div class="container">
    <div class="header">
      <h1>文件转换中</h1>

      <p class="subtitle">请稍等，我们正在处理您的文件</p>
    </div>

    <div class="spinner"></div>

    <div class="file-info">
      <h3>正在处理的文件</h3>

      <p class="file-name" id="fileName">${fileName}</p>
    </div>

    <div class="message" id="message">
      ${message}...
    </div>

    <div class="countdown-section">
      <p class="countdown-text">页面将在<span id="countdown">${time}</span>秒后自动刷新</p>
    </div>

    <div class="controls">
      <button class="btn btn-primary" id="refreshBtn">立即刷新</button>
    </div>

    <div class="tips">
      <h4>提示</h4>

      <ul>
        <li>文件转换时间取决于文件大小和服务器负载</li>

        <li>转换完成后，页面将自动跳转到预览页面</li>

        <li>您也可以点击&quot;立即刷新&quot;按钮手动检查转换状态</li>
      </ul>
    </div>

    <div class="footer">
      <p>预计剩余时间:<span id="estimatedTime">约 1 分钟</span></p>

      <p style="margin-top: 5px;">如有问题，请联系技术支持</p>
    </div>
  </div><script>
let countdown = ${time};
let countdownInterval;

// 删除forceUpdatedCache参数并更新URL
function cleanForceUpdateParam() {
    const url = new URL(window.location.href);
    const params = new URLSearchParams(url.search);
    
    if (params.has('forceUpdatedCache')) {
        params.delete('forceUpdatedCache');
        
        // 构建新的URL
        const newSearch = params.toString();
        const newUrl = url.origin + url.pathname + (newSearch ? '?' + newSearch : '');
        
        // 使用history.replaceState更新URL而不刷新页面
        window.history.replaceState({}, document.title, newUrl);
        console.log('已移除forceUpdatedCache参数，当前URL:', newUrl);
    }
}

function startCountdown() {
    const countdownElement = document.getElementById('countdown');
    countdownInterval = setInterval(() => {
        if (countdown > 0) {
            countdown--;
            countdownElement.textContent = countdown;
        } else {
            clearInterval(countdownInterval);
            window.location.reload();
        }
    }, 1000);
}

document.getElementById('refreshBtn').addEventListener('click', function() {
    window.location.reload();
});

// 页面加载后执行
window.addEventListener('load', function() {
    // 先清理URL参数
    cleanForceUpdateParam();
    
    // 然后开始倒计时
    startCountdown();
});
</script>
</body>
</html>
