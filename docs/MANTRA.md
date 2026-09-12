# This document contains 'mantras', pieces of information that describe facets of the software that should be considered complete, set in stone, and should not be adjusted unless explicitly specified by the user.

## The Navigation Pill Springiness & Bounciness (`Spring.DampingRatioMediumBouncy`)

**Status:** Set in Stone / Immutable.

**Description:** 
The navigation pill (`ExpressiveNavigationBar` and `ExpressiveNavItem`) in Sustenance utilizes `Spring.DampingRatioMediumBouncy` for all spring animations (scale releases, selection alpha transitions, icon scaling, content size animations, and today/detail transforms). 

**Rule:**
Never regress, alter, or replace this bounciness/springiness logic with lower bounciness (such as `DampingRatioLowBouncy`) or rigid animations. The navigation pill behavior and its delightful medium-bouncy spring mechanics are finalized, set in stone, and must be preserved across all future updates and refactors. Do not reverse progress on the navigation pill.
