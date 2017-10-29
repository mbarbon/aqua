// @flow
class PubSubBase<R> {
  subscribers: Array<R>

  constructor () {
    this.subscribers = []
  }

  subscribe (fnc: R) {
    this.subscribers.push(fnc)
  }

  unsubscribe (fnc: R) {
    this.subscribers.splice(this.subscribers.indexOf(fnc), 1)
  }

  notify (...args: Array<any>) {
    for (let i = 0; i < this.subscribers.length; ++i) {
      try {
        // $FlowFixMe
        this.subscribers[i].apply(null, arguments)
      } catch (err) {
        console.error(err)
      }
    }
  }
}

class PubSub0 extends PubSubBase<() => void> {
  notify () {
    super.notify()
  }
}

class PubSub1<T> extends PubSubBase<(T) => void> {
  notify (p1: T) {
    super.notify(p1)
  }
}

class PubSub2<T, U> extends PubSubBase<(T, U) => void> {
  notify (p1: T, p2: U) {
    super.notify(p1, p2)
  }
}

class PubSub3<T, U, V> extends PubSubBase<(T, U, V) => void> {
  notify (p1: T, p2: U, p3: V) {
    super.notify(p1, p2, p3)
  }
}

export { PubSub0, PubSub1, PubSub2, PubSub3 }
