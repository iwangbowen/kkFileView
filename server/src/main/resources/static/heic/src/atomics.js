console.log('load polyfill for Atomics');

class Atomics {
  static store(arr, index, value)  {
    arr[index] = value;
  }
  static compareExchange(arr, index, expectValue, newValue) {
    const value = arr[index];
    if (value == expectValue) {
      arr[index] = newValue;
    }
    return value;
  }
}
