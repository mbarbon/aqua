// @flow
type Receiver0 = () => void
type Receiver1 = <V>(V) => void
type Receiver2 = <V, U>(V, U) => void
type Receiver3 = <V, U, T>(V, U, T) => void

interface PubSub0 {
  subscribe(fnc: Receiver0): void;
  unsubscribe(fnc: Receiver0): void;
  notify(): void;
}

interface PubSub1<V> {
  subscribe(fnc: Receiver1<V>): void;
  unsubscribe(fnc: Receiver1<V>): void;
  notify(V): void;
}

interface PubSub2<V, U> {
  subscribe(fnc: Receiver2<V, U>): void;
  unsubscribe(fnc: Receiver2<V, U>): void;
  notify(V, U): void;
}

interface PubSub3<V, U, T> {
  subscribe(fnc: Receiver3<V, U, T>): void;
  unsubscribe(fnc: Receiver3<V, U, T>): void;
  notify(V, U, T): void;
}

export default class PubSub {
  subscribers: Array<any>

  constructor () {
    this.subscribers = []
  }

  subscribe (fnc: any) {
    this.subscribers.push(fnc)
  }

  unsubscribe (fnc: any) {
    this.subscribers.splice(this.subscribers.indexOf(fnc), 1)
  }

  notify (...args: Array<any>) {
    for (let i = 0; i < this.subscribers.length; ++i) {
      try {
        this.subscribers[i].apply(null, arguments)
      } catch (err) {
        console.error(err)
      }
    }
  }
}

export type { PubSub0, PubSub1, PubSub2, PubSub3 }
