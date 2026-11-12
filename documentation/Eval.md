# `Eval.java`

Source: [`src/tablutai/Eval.java`](../src/tablutai/Eval.java)

## Purpose

`Eval` estimates a non-terminal position using a weighted linear sum. `evalWhite()` always returns a score from White's perspective; `evalSide()` changes the sign when Black is to move so negamax can use one maximizing convention.

## Code map

The feature indexes, names, and default weights define an 11-element tuning vector. The evaluation starts with soldier material, then considers the king's available escape rays, one-move mobility and threats, hostile adjacent squares, distance to the edge, and nearby white/black soldiers. Feature contributions are capped where a large count would otherwise dominate the score. Constructors either copy defaults or supplied weights.

## Design decisions

Keeping feature weights in one array makes offline tuning and self-play comparisons straightforward. Scores are integer-valued for speed and predictable search comparisons. The evaluation does not replace exact terminal checks: wins, losses, and immediate king captures are handled by `Search` with mate scores.
