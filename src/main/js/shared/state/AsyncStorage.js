// @flow
// XXX this should not be in the shared folder
const storage = window.localStorage

export class AsyncStorage {
  static getItem (key) {
    return Promise.resolve(storage.getItem(key))
  }

  static setItem (key, value) {
    storage.setItem(key, value)
    return Promise.resolve()
  }

  static removeItem (key) {
    storage.removeItem(key)
    return Promise.resolve()
  }

  static multiGet (keys) {
    return Promise.resolve(keys.map(k => [k, storage.getItem(k)]))
  }

  static multiSet (values) {
    for (let [k, v] of values) {
      storage.setItem(k, v)
    }
    return Promise.resolve()
  }

  static multiRemove (keys) {
    keys.map(storage.removeItem.bind(storage))
    return Promise.resolve()
  }
}
