import { readMapColors } from './mapColors';

const tokens = {
  '--color-availability-good': '#15803d',
  '--color-availability-good-foreground': '#ffffff',
  '--color-availability-low': '#fbbf24',
  '--color-availability-low-foreground': '#030712',
  '--color-availability-empty': '#b91c1c',
  '--color-availability-empty-foreground': '#ffffff',
  '--color-availability-off': '#6b7280',
  '--color-availability-off-foreground': '#ffffff',
  '--color-map-user-location': '#2563eb',
};

describe('readMapColors', () => {
  afterEach(() => {
    for (const token of Object.keys(tokens)) document.documentElement.style.removeProperty(token);
  });

  it('reads the availability and user location colors from the theme tokens', () => {
    for (const [token, value] of Object.entries(tokens)) {
      document.documentElement.style.setProperty(token, value);
    }

    expect(readMapColors()).toEqual({
      tones: {
        good: { fill: '#15803d', text: '#ffffff' },
        low: { fill: '#fbbf24', text: '#030712' },
        empty: { fill: '#b91c1c', text: '#ffffff' },
        off: { fill: '#6b7280', text: '#ffffff' },
      },
      userLocation: '#2563eb',
    });
  });
});
