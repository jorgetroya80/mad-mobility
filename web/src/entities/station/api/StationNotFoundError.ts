export class StationNotFoundError extends Error {
  readonly number: string;

  constructor(number: string) {
    super(`Station ${number} not found`);
    this.name = 'StationNotFoundError';
    this.number = number;
  }
}
