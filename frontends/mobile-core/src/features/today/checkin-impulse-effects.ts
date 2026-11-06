import type { CSSProperties } from 'vue'

const localFlights = [
  [-127, -128, 39, -24, -32, -36, 0],
  [-62, -160, 31, 36, 24, 22, 25],
  [62, -165, 42, -30, 36, -18, 8],
  [130, -116, 34, 26, -33, 36, 48],
  [-151, -42, 34, 40, 26, -27, 28],
  [153, -18, 45, -35, -26, 31, 0],
  [-133, 69, 46, -26, 35, 18, 62],
  [112, 104, 31, 34, -30, -39, 42],
  [-58, 132, 28, -32, 32, -22, 20],
  [55, 144, 36, 28, -27, 27, 60],
  [-101, -180, 24, 36, 38, 46, 75],
  [109, -182, 26, -36, -31, -37, 85],
] as const
const sideTargets = [
  [-1, 0.36, 64],
  [1, 0.4, 56],
  [-1, 0.58, 72],
  [1, 0.55, 74],
  [-1, 0.73, 56],
  [1, 0.72, 65],
  [-1, 0.48, 65],
  [1, 0.48, 66],
  [-1, 0.65, 59],
  [1, 0.64, 69],
  [-1, 0.42, 71],
  [1, 0.37, 62],
] as const
const lowerTargets = [
  [-0.065, 70],
  [0.5, 60],
  [1.065, 70],
  [0.04, 83],
  [0.545, 85],
  [0.96, 83],
  [-0.115, 95],
  [0.455, 105],
  [1.115, 95],
  [0.11, 92],
  [0.578, 76],
  [0.89, 92],
] as const

const designRem = (value: number): string => `${value / 16}rem`
export function measuredRem(cssPixels: number): string {
  const rootSize = parseFloat(getComputedStyle(document.documentElement).fontSize) || 16
  return `${cssPixels / rootSize}rem`
}

function coinStyle(
  x: string,
  y: string,
  size: number,
  rx: number,
  ry: number,
  rz: number,
  delay: number,
  duration: number,
): CSSProperties {
  return {
    '--impulse-x': x,
    '--impulse-y': y,
    '--impulse-size': designRem(size),
    '--impulse-rx': `${rx}deg`,
    '--impulse-ry': `${ry}deg`,
    '--impulse-rz': `${rz}deg`,
    '--impulse-delay': `${delay}ms`,
    '--impulse-duration': `${duration}ms`,
  }
}

export function impulseCoins(
  x: number,
  y: number,
  width: number,
  height: number,
): { direction: string; style: CSSProperties }[] {
  const narrow = Math.min(1, width / 390)
  const local = localFlights.map(([dx, dy, size, rx, ry, rz, delay]) => ({
    direction: 'local',
    style: coinStyle(designRem(dx * narrow), designRem(dy), size, rx, ry + 360, rz, delay, 1650),
  }))
  const targets = [
    ...sideTargets.map(([side, fraction, beyond]) => ({
      direction: side < 0 ? 'left' : 'right',
      endX: side < 0 ? -beyond : width + beyond,
      endY: height * fraction,
    })),
    ...lowerTargets.map(([fraction, beyond], index) => ({
      direction: ['down-left', 'down', 'down-right'][index % 3]!,
      endX: width * fraction,
      endY: height + beyond,
    })),
  ]
  return local.concat(
    targets.map(({ direction, endX, endY }, index) => ({
      direction,
      style: coinStyle(
        measuredRem(endX - x),
        measuredRem(endY - y),
        23 + ((index * 11) % 17),
        ((index * 17) % 75) - 37,
        (index % 2 ? -1 : 1) * (290 + ((index * 51) % 125)),
        ((index * 33) % 155) - 77,
        index < 12 ? (index * 37) % 149 : 72 + ((index * 37) % 139),
        index < 12 ? 1260 + ((index * 43) % 191) : 1400 + ((index * 43) % 142),
      ),
    })),
  )
}

export const impulseDust = Array.from(
  { length: 27 },
  (_, index): CSSProperties => ({
    '--impulse-dust-x': `${(7 + index * 29) % 100}%`,
    '--impulse-dust-y': `${(5 + index * 43) % 100}%`,
    '--impulse-dust-size': `${0.1 + (index % 4) * 0.045}rem`,
    '--impulse-dust-opacity': 0.18 + (index % 5) * 0.055,
    '--impulse-dust-period': `${8 + (index % 5) * 2}s`,
    '--impulse-dust-delay': `-${index % 7}s`,
    '--impulse-dust-drift': index % 2 ? '-1.5rem' : '1.6rem',
  }),
)

export function impulseConfetti(
  points: NodeListOf<HTMLElement>,
  x: number,
  y: number,
): CSSProperties[] {
  return Array.from(points, (point, index) => {
    const rect = point.getBoundingClientRect()
    const pose = getComputedStyle(point).transform
    const direction = Math.atan2(rect.top + rect.height / 2 - y, rect.left + rect.width / 2 - x)
    const distance = 58 + ((index * 29) % 115)
    return {
      '--impulse-confetti-from': pose === 'none' ? 'translate3d(0, 0, 0)' : pose,
      '--impulse-confetti-x': designRem(Math.cos(direction) * distance),
      '--impulse-confetti-y': designRem(Math.sin(direction) * distance),
      '--impulse-confetti-turn': `${(index % 2 ? -1 : 1) * (90 + ((index * 43) % 210))}deg`,
      '--impulse-confetti-time': `${840 + ((index * 29) % 430)}ms`,
    }
  })
}
