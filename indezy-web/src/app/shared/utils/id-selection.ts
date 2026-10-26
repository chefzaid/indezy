/** The records picked in a list, by id, in the order they were picked. */
export class IdSelection {
  private picked: number[] = [];

  get ids(): number[] {
    return [...this.picked];
  }

  get size(): number {
    return this.picked.length;
  }

  has(id: number | undefined): boolean {
    return id !== undefined && this.picked.includes(id);
  }

  toggle(id: number | undefined): void {
    if (id === undefined) {
      return;
    }
    this.picked = this.has(id) ? this.picked.filter(picked => picked !== id) : [...this.picked, id];
  }

  /** Whether every one of {@code ids} is picked (false for none). */
  hasAll(ids: (number | undefined)[]): boolean {
    return ids.length > 0 && ids.every(id => this.has(id));
  }

  /** Whether some, but not all, of {@code ids} are picked. */
  hasSome(ids: (number | undefined)[]): boolean {
    return ids.some(id => this.has(id)) && !this.hasAll(ids);
  }

  /** Picks all of {@code ids}, or leaves them all when they all were. */
  toggleAll(ids: (number | undefined)[]): void {
    const known = ids.filter((id): id is number => id !== undefined);
    this.picked = this.hasAll(known)
      ? this.picked.filter(id => !known.includes(id))
      : [...this.picked, ...known.filter(id => !this.picked.includes(id))];
  }

  /** Keeps only the picked ids still among {@code ids}, after a reload. */
  retain(ids: (number | undefined)[]): void {
    this.picked = this.picked.filter(id => ids.includes(id));
  }

  clear(): void {
    this.picked = [];
  }
}
