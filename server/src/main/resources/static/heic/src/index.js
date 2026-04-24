(function() {
  const workerUrl = '/heic/src/worker.js';
  const worker = new Worker(workerUrl);
  const promisePool = {};
  const statFuncPool = {};

  worker.onmessage = e => {
    if (e.data.console) {
     // console.log(...e.data.console);
    } else if (e.data.stat) {
      statFuncPool[e.data.url](e.data.stat);
    } else if (e.data.convertToBlob) {
      ConvertRgbaToPng(e.data);
    } else {
      promisePool[e.data.url](e.data.urlPng);
      delete promisePool[e.data.url];
     // console.log('Convert Done:', e.data.url);
      statFuncPool[e.data.url]('图片解析成功');
      delete statFuncPool[e.data.url];
    }
  };

  async function ConvertRgbaToPng(args) {
    try {
    //console.log('convertToBlob', args.url, args.width, args.height);
      const canvas = document.createElement('canvas');
      canvas.width = args.width;
      canvas.height = args.height;
      const ctx = canvas.getContext(...args.getContext);
      ctx.putImageData(...args.putImageData);
      canvas.toBlob(
        blob => {
          worker.postMessage({
            'url': args.url,
            'blob': blob,
          });
        }, 
        args.convertToBlob.type,
        args.convertToBlob.quality
      );
    } catch (e) {
      console.log(e);
      statFuncPool[args.url]('图片解析失败，请尝试刷新: ' + (e.toString ? e.toString() : JSON.stringify(e)));
    }
  }

  async function ConvertHeicToPng(url, statFunc) {
    //console.log('ConvertHeicToPng:', url);
    statFunc('图片等待解析');
    statFuncPool[url] = statFunc;
    const promise = new Promise(function(resolve) {
      promisePool[url] = resolve;
      worker.postMessage({'url': url});
    });
    return promise;
  }
  document.ConvertHeicToPng = ConvertHeicToPng;
})()
