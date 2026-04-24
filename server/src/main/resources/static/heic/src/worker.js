(function() {
  importScripts('/heic/src/wasm_heif.js');
  const cacheName = 'ConvertHeicToPng';
  const cacheVersion = 'r=11';

  console.print = console.log;
  console.log = function(...args) {
    try {
      postMessage({console: args});
    } catch {
      console.print(...args);
    }
  }

  if (typeof Atomics == 'undefined') {
    importScripts('/heic/src/atomics.js');
  }

  if (typeof OffscreenCanvas == 'undefined') {
    importScripts('/heic/src/offscreencanvas.js');
  }

  const jobQueue = {
    lock: new Uint8Array(1),
    queue: [],
    work: null,
    run: async function(msg) {
      await this.work(msg);
      this.next();
    },
    next: function() {
      if (this.queue.length < 1) {
        Atomics.store(this.lock, 0, 0);
        return;
      }
      this.run(this.queue.shift());
    },
    add: function(msg) {
      if (Atomics.compareExchange(this.lock, 0, 0, 1) == 0) {
        this.run(msg);
        return;
      }
      this.queue.push(msg);
    },
  };

  async function convertHeicToPng(url) {
    try {
      console.log('download image...');
      postMessage({url: url, stat: '正在下载图片'});
      const data = await fetch(url);
      const array = new Uint8Array(await data.arrayBuffer());
      if (array.length < 12) {
        postMessage({url: url, stat: '不是图片文件'});
        return;
      }
      const isAVIF = (array[8] == 0x61 /*'a'*/);
      const decodeFunc = isAVIF ? wasm_avif : wasm_heif;
      const decoder = await decodeFunc({
          onRuntimeInitialized() {
            console.log('decode '+(isAVIF ? 'avif' : 'heif')+'...');
            postMessage({url: url, stat: '正在解析图片文件'});
          },
      });
      const rgba = decoder.decode(array, array.length, true);
      const dim = decoder.dimensions();
      decoder.free();

      console.log('draw to canvas...', dim);
      postMessage({url: url, stat: '正在绘制图片数据'});
      console.log('rgba.length:', rgba.length, 'expected:', dim.width * dim.height * 4);
      const canvas = new OffscreenCanvas(dim.width, dim.height);
      canvas._url = url; // set unique id for polyfill
      const ctx = canvas.getContext("2d");
      const imgData = new ImageData(Uint8ClampedArray.from(rgba),
        dim.width, dim.height);
      ctx.putImageData(imgData, 0, 0);

      console.log('convert to jpeg...');
      postMessage({url: url, stat: '正在转换图片格式'});
      const blob = await canvas.convertToBlob({
        type: 'image/jpeg',
        quality: 0.75,
      });

      // cache blob
      try {
        const cache = await caches.open(cacheName);
        const options = {statusText: cacheVersion}
        cache.put(new Request(url), new Response(blob, options));
      } catch(e) {
        // ignore
        console.log(e);
      }
      return URL.createObjectURL(blob);
    } catch (e) {
      // something went wrong
      console.log(e);
      postMessage({
        url: url,
        stat: '图片解析失败，请尝试刷新: ' + (e.toString ? e.toString() : JSON.stringify(e)),
      });
      return null;
    }
  }

  jobQueue.work = async function (data) {
    const urlPng = await convertHeicToPng(data.url);
    if (urlPng) {
      data.urlPng = urlPng;
      postMessage(data);
    }
  }

  async function findCacheOrAddJob(data) {
    // find blob from cache
    try {
      const cache = await caches.open(cacheName);
      const response = await cache.match(new Request(data.url));
      if (response && response.statusText == cacheVersion) {
        console.log('Found from Cache:', response.statusText, data.url);
        postMessage({url: data.url, stat: '已发现图片缓存'});
        const blob = await response.blob();
        const urlPng = URL.createObjectURL(blob);
        if (urlPng) {
          data.urlPng = urlPng;
          postMessage(data);
          return;
        }
      }
    } catch(e) {
      // ignore
      console.log(e);
    }
    // cannot use a cache
    jobQueue.add(data);
  }

  // web worker
  onmessage = function (e) {
    if (e.data.blob) {
      OffscreenCanvas.resolve(e.data);
    } else {
      findCacheOrAddJob(e.data);
    }
  }
})()
