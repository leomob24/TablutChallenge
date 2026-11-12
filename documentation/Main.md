# `Main.java`

Source: [`src/tablutai/Main.java`](../src/tablutai/Main.java)

## Purpose

`Main` validates command-line arguments, chooses player role and connection settings, and starts the protocol client.

## Code map

The first argument is required and accepts `WHITE` or `BLACK` without regard to case. Optional arguments set timeout seconds, server IP, and player name. Missing optional values use a 60-second timeout, `localhost`, and `VikingAB`. Invalid role exits with an error; an invalid timeout reports the problem and retains the default.

## Design decisions

Argument parsing is kept at the entry point so the networking/search classes do not depend on command-line conventions. The role is normalized once, then represented internally as a boolean used to select the server port and compare turns.
