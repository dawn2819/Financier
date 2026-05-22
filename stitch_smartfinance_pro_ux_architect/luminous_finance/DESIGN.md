---
name: Luminous Finance
colors:
  surface: '#0f150e'
  surface-dim: '#0f150e'
  surface-bright: '#353b33'
  surface-container-lowest: '#0a1009'
  surface-container-low: '#171d16'
  surface-container: '#1b211a'
  surface-container-high: '#262c24'
  surface-container-highest: '#30362e'
  on-surface: '#dee4d9'
  on-surface-variant: '#becab9'
  inverse-surface: '#dee4d9'
  inverse-on-surface: '#2c322a'
  outline: '#899484'
  outline-variant: '#3f4a3c'
  surface-tint: '#78dc77'
  primary: '#78dc77'
  on-primary: '#00390a'
  primary-container: '#4caf50'
  on-primary-container: '#003c0b'
  inverse-primary: '#006e1c'
  secondary: '#9ecaff'
  on-secondary: '#003258'
  secondary-container: '#1e95f2'
  on-secondary-container: '#002b4d'
  tertiary: '#ffb1c7'
  on-tertiary: '#650032'
  tertiary-container: '#f26f9d'
  on-tertiary-container: '#690034'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#94f990'
  primary-fixed-dim: '#78dc77'
  on-primary-fixed: '#002204'
  on-primary-fixed-variant: '#005313'
  secondary-fixed: '#d1e4ff'
  secondary-fixed-dim: '#9ecaff'
  on-secondary-fixed: '#001d36'
  on-secondary-fixed-variant: '#00497d'
  tertiary-fixed: '#ffd9e2'
  tertiary-fixed-dim: '#ffb1c7'
  on-tertiary-fixed: '#3e001c'
  on-tertiary-fixed-variant: '#861948'
  background: '#0f150e'
  on-background: '#dee4d9'
  surface-variant: '#30362e'
typography:
  h1:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.02em
  h2:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-caps:
    fontFamily: Inter
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  container-margin: 16px
  gutter: 16px
---

## Brand & Style

This design system is built on the intersection of **Material 3 functionalism** and **Modern Glassmorphism**. The brand personality is professional yet approachable, designed to instill confidence in financial management while maintaining a "smooth" digital-first feel. 

The aesthetic leverages high-contrast dark mode surfaces with subtle translucent overlays to create a sense of depth and hierarchy. It avoids the coldness of traditional banking apps by using friendly, rounded corners and soft lighting effects, ensuring the interface feels responsive and alive.

## Colors

The palette is optimized for a deep dark-mode experience, utilizing a rich Slate background to reduce eye strain while making the Primary Finance Green pop. 

- **Primary (#4CAF50):** Used for growth indicators, primary actions, and "Success" states.
- **Secondary (#2196F3):** Used for information callouts, links, and secondary interactive elements.
- **Accent (#FF9800):** Reserved for warnings, pending states, or attention-grabbing highlights.
- **Surface Strategy:** The UI uses `#1E293B` as the base card color, providing enough contrast against the `#0F172A` background to establish clear containment without harsh borders.

## Typography

The system utilizes **Inter** for its exceptional readability in data-heavy environments. The typographic hierarchy is structured to prioritize legibility of financial figures. 

- **Headlines:** Bold weights are used for account balances and screen titles to provide immediate visual anchors.
- **Body:** Standardized at 14px and 16px to ensure accessibility across various device densities.
- **Captions:** Used for timestamps, metadata, and micro-copy, maintaining a medium weight to ensure they remain legible against dark backgrounds.

## Layout & Spacing

This design system adheres to a strict **4pt grid system**. All dimensions, padding, and margins must be multiples of 4.

- **Grid Model:** A fluid grid is employed for mobile screens.
- **Margins:** A standard 16px (md) margin is applied to the left and right of the screen container.
- **Vertical Rhythm:** Elements should typically be separated by 16px or 24px to maintain a breathable, minimal feel.
- **Touch Targets:** Ensure all interactive elements maintain a minimum size of 48x48dp, even if their visual representation is smaller.

## Elevation & Depth

Hierarchy is established through **Tonal Elevation** and **Glassmorphism**, rather than traditional heavy shadows.

- **Level 1 (Background):** #0F172A - The lowest layer.
- **Level 2 (Cards):** #1E293B - Used for content grouping.
- **Level 3 (Modals/Overlays):** A semi-transparent version of the surface color (80% opacity) with a 20px background blur (Backdrop Filter) to create the "Glass" effect.
- **Shadows:** Use extremely soft, low-opacity shadows (Color: #000000, Alpha: 0.2, Blur: 12px) only on primary Floating Action Buttons (FABs) to make them appear physically lifted above the scrolling content.

## Shapes

The design language favors a "soft-modern" look. 

- **Cards & Containers:** Default to 16px (rounded-lg) to match the Material 3 aesthetic while appearing friendlier than sharp corners.
- **Buttons:** Use fully rounded (pill-shaped) profiles for high-emphasis actions like "Transfer" or "Send".
- **Inputs:** Utilize a 12px corner radius to distinguish them slightly from larger container cards.

## Components

### Buttons & FABs
- **Primary FAB:** A pill-shaped or rounded-square button using the Primary Green. It should be positioned in the bottom right or centered in the bottom nav. 
- **Glass Buttons:** Use for secondary actions on top of colorful backgrounds—semi-transparent white (10% opacity) with a background blur.

### Cards
- **Main Cards:** 16px corner radius, #1E293B background. No border.
- **Glass Accents:** For high-priority data (e.g., "Current Balance" header), use a translucent glass card with a subtle 1px white border at 10% opacity to define the edge.

### Bottom Navigation
- **Structure:** 5 tabs centered. Active state uses the Primary Green for the icon and a subtle glow or pill-shaped background indicator.
- **Style:** Use a glassmorphic background (blur: 15px, opacity: 90%) to allow content to scroll behind it.

### Inputs & Lists
- **Text Fields:** Outlined or filled with #1E293B. Labels in Text Secondary.
- **Transaction Lists:** Clean, no-border rows separated by a 1px divider (#2D3748) or simply by whitespace. Large iconography (40x40px) with 12px rounded backgrounds for category icons.

### Progress Indicators
- Use slim, rounded linear progress bars for budget tracking. Use Secondary Blue for "in progress" and Accent Orange for "near limit".