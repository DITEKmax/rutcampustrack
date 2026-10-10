/** Commands share one order so an older mutation count cannot arrive after a newer ACK. */
export class NotificationsMutationQueue {
  private tail: Promise<unknown> = Promise.resolve()
  run<T>(operation: () => Promise<T>): Promise<T> {
    const task = this.tail.then(operation)
    this.tail = task.catch(() => undefined)
    return task
  }
}
