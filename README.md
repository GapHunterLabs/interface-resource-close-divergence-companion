# Interface Resource-Close Divergence Companion

Flags a call through an interface-typed reference, passing a resource,
where real implementations diverge on guaranteeing to close it.

## Why it exists

CWE-772, combined with the Liskov-style concern
`interface-exception-divergence-companion` already established: a
caller that only ever exercised ONE implementation (which reliably
closes the resource) never sees the leak until dependency injection
swaps in a different real implementation in production.

## Why built this way

- **A triple combination, not a pair** -- this catalog's other
  "combination" plugins (`interprocedural-resource-leak-companion`,
  `interface-sink-divergence-companion`) each combine TWO previously-
  separate techniques. This one combines THREE: real Class Hierarchy
  Analysis (`interface-exception-divergence-companion`'s own
  `ClassInheritorsSearch`), a real Tarjan-SCC interprocedural fixed
  point (`log-injection-companion`'s own technique), and a
  branch-merging path-sensitive engine
  (`jdbc-double-close-companion`'s own technique) -- all reused
  verbatim (each file copied per this catalog's "no shared library"
  convention) and composed into one mechanism.
- **No new analysis code needed for the interprocedural/path-sensitive
  half** -- `ProjectResourceCloseSummaryAnalyzer`'s whole-project
  summary map already has an entry for every project method with a
  resource-typed parameter, including each interface implementation's
  own override (just another regular method in the scan). The only
  genuinely NEW code this plugin adds is the CHA-based comparison
  across implementations.

## v0.1 scope — stated honestly, not exhaustively

- Only interfaces with 2-10 real implementations in the project.
- Same resource types
  (`Connection`/`Statement`/`PreparedStatement`/`CallableStatement`/
  `ResultSet`/`InputStream`/`OutputStream`/`Reader`/`Writer`) and the
  same path-sensitivity limits (conservative `catch`-block state, one
  loop iteration merged with zero) as its two base mechanisms.

## Usage

Open a Java file with an interface with 2+ implementations that
diverge on guaranteeing to close a resource parameter, called through
the interface type -- the call site shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
