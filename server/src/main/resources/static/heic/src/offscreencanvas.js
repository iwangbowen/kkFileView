console.log('load polyfill for OffscreenCanvas');

class OffscreenCanvas {
  static promisePool = {}
  static resolve(msg) {
    OffscreenCanvas.promisePool[msg.url](msg.blob);
    delete OffscreenCanvas.promisePool[msg.url];
  }
  constructor(width, height) {
    this.args = {
      width: width,
      height: height,
    };
  }
  set _url(url) {
    this.args.url = url;
  }
  getContext(...args) {
    this.args.getContext = args;
    return this;
  }
  putImageData(...args) {
    this.args.putImageData = args;
  }
  convertToBlob(opt) {
    return new Promise(resolve => {
      OffscreenCanvas.promisePool[this.args.url] = resolve;
      this.args.convertToBlob = opt || {};
      postMessage(this.args);
    });
  }
}
