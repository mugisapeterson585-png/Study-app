import { AnswerItem } from '../types';

export const SAMPLE_ANSWERS: AnswerItem[] = [
  {
    id: 1,
    subject: 'Mathematics',
    questionText: 'Solve the quadratic equation 2x^2 + 5x - 3 = 0 using the quadratic formula.',
    imagePaths: [
      'https://images.unsplash.com/photo-1635070041078-e363dbe005cb?w=600&auto=format&fit=crop&q=80'
    ],
    shortAnswer: 'The solutions to the quadratic equation are x = 1/2 and x = -3.',
    simpleExplanation: 'We use the quadratic formula to find where this parabola crosses the horizontal axis. By plugging in the coefficients 2, 5, and -3, we find two roots.',
    blocks: [
      {
        type: 'text',
        display: 'Identify the coefficients in the standard form $ax^2 + bx + c = 0$:',
        spoken: 'First identify the coefficients from standard form.'
      },
      {
        type: 'step',
        stepNumber: 1,
        display: 'Here, $a = 2$, $b = 5$, and $c = -3$.',
        spoken: 'Step 1: Here a equals 2, b equals 5, and c equals negative 3.'
      },
      {
        type: 'step',
        stepNumber: 2,
        display: 'Recall the quadratic formula: $$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}$$',
        spoken: 'Step 2: Recall the quadratic formula: x equals negative b plus or minus the square root of b squared minus 4 a c, all over 2 a.'
      },
      {
        type: 'step',
        stepNumber: 3,
        display: 'Compute the discriminant: $b^2 - 4ac = 5^2 - 4(2)(-3) = 25 + 24 = 49$.',
        spoken: 'Step 3: Compute the discriminant: 5 squared minus 4 times 2 times negative 3 equals 25 plus 24, which equals 49.'
      },
      {
        type: 'step',
        stepNumber: 4,
        display: 'Evaluate the square root: $\\sqrt{49} = 7$.',
        spoken: 'Step 4: The square root of 49 is 7.'
      },
      {
        type: 'step',
        stepNumber: 5,
        display: 'Calculate the two roots: $$x_1 = \\frac{-5 + 7}{4} = \\frac{2}{4} = \\frac{1}{2}, \\quad x_2 = \\frac{-5 - 7}{4} = \\frac{-12}{4} = -3$$',
        spoken: 'Step 5: Therefore x equals negative 5 plus or minus 7, all over 4. This yields x equals one half, and x equals negative 3.'
      }
    ],
    starred: true,
    createdAt: Date.now() - 86400000 * 2,
    lastPlayedAt: Date.now() - 3600000 * 4,
    quizStats: {
      timesReviewed: 3,
      timesCorrect: 3,
      lastReviewedAt: Date.now() - 3600000 * 4,
      intervalDays: 4,
      easeFactor: 2.5
    }
  },
  {
    id: 2,
    subject: 'Physics',
    questionText: 'A 1200 kg car travelling at 20 m/s brakes to a complete stop over a distance of 40 metres. Find the average braking force exerted on the car.',
    imagePaths: [
      'https://images.unsplash.com/photo-1509228468518-180dd4864904?w=600&auto=format&fit=crop&q=80'
    ],
    shortAnswer: 'The average braking force exerted on the car is 6,000 Newtons in the direction opposite to motion.',
    simpleExplanation: 'The car loses all its kinetic energy as work done by friction over the 40 metre stopping distance. Dividing the total kinetic energy by 40 metres yields the braking force.',
    blocks: [
      {
        type: 'text',
        display: 'We can solve this using either the Work-Energy Theorem or kinematic equations.',
        spoken: 'We apply the work energy theorem.'
      },
      {
        type: 'step',
        stepNumber: 1,
        display: 'Initial kinetic energy is: $$E_k = \\frac{1}{2} m v^2 = \\frac{1}{2} (1200)(20^2) = 600 \\times 400 = 240,000\\text{ Joules}$$',
        spoken: 'Step 1: The initial kinetic energy is one half m v squared, which equals one half times 1200 times 20 squared, resulting in 240,000 Joules.'
      },
      {
        type: 'step',
        stepNumber: 2,
        display: 'Work done by the braking force over distance $d = 40\\text{ m}$ is: $$W = F \\times d$$',
        spoken: 'Step 2: The work done by friction equals force times distance.'
      },
      {
        type: 'step',
        stepNumber: 3,
        display: 'Equating work to kinetic energy loss: $$F = \\frac{E_k}{d} = \\frac{240,000}{40} = 6,000\\text{ N}$$',
        spoken: 'Step 3: Dividing 240,000 Joules by 40 metres gives an average braking force of 6,000 Newtons.'
      }
    ],
    starred: false,
    createdAt: Date.now() - 86400000,
    lastPlayedAt: Date.now() - 3600000 * 24,
    quizStats: {
      timesReviewed: 1,
      timesCorrect: 1,
      lastReviewedAt: Date.now() - 3600000 * 24,
      intervalDays: 2,
      easeFactor: 2.5
    }
  },
  {
    id: 3,
    subject: 'Biology',
    questionText: 'Outline the light-dependent reactions of photosynthesis and where they take place inside the chloroplast.',
    imagePaths: [],
    shortAnswer: 'The light-dependent reactions occur in the thylakoid membranes, converting solar photons into chemical energy in ATP and NADPH while releasing oxygen.',
    simpleExplanation: 'Think of thylakoid discs like tiny solar panels inside plant cells. Sunlight splits water molecules, generates oxygen you breathe, and charges up molecular batteries.',
    blocks: [
      {
        type: 'text',
        display: 'Location: **Thylakoid membranes** arranged in grana stacks within the chloroplast stroma.',
        spoken: 'Location: Thylakoid membranes within the chloroplast.'
      },
      {
        type: 'step',
        stepNumber: 1,
        display: '1. **Photolysis of Water**: In Photosystem II, light photons split water molecules: $$2\\text{H}_2\\text{O} \\rightarrow \\text{O}_2 + 4\\text{H}^+ + 4e^-$$',
        spoken: 'Step 1: Photolysis of water in photosystem 2. Photons split water into oxygen gas, protons, and excited electrons.'
      },
      {
        type: 'step',
        stepNumber: 2,
        display: '2. **Electron Transport Chain & Chemiosmosis**: Electrons travel down cytochromes, pumping protons into the thylakoid lumen, driving ATP synthase.',
        spoken: 'Step 2: Electron transport and chemiosmosis create a proton gradient that powers ATP synthase to produce ATP.'
      },
      {
        type: 'step',
        stepNumber: 3,
        display: '3. **NADPH Formation**: Electrons reach Photosystem I and are accepted by NADP+ to form NADPH for the subsequent Calvin cycle.',
        spoken: 'Step 3: At photosystem 1, electrons reduce NADP positive into NADPH, ready for the Calvin cycle.'
      }
    ],
    starred: true,
    createdAt: Date.now() - 3600000 * 12,
    quizStats: {
      timesReviewed: 2,
      timesCorrect: 2,
      lastReviewedAt: Date.now() - 3600000 * 12,
      intervalDays: 3,
      easeFactor: 2.5
    }
  }
];
