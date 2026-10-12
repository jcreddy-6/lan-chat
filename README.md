# LAN Chat and File Sharing System (Core Java)

Pure Java, no frameworks. Only external file: a JDBC driver jar (put it in `lib/` later).

## Build and run (no Maven needed)
```
# Linux / macOS
javac -d out $(find src -name "*.java")
java -cp out com.lanchat.common.Phase1Demo
# Phase 2 needs the sqlite jar from lib/ on the classpath:
java -cp "out:lib/*" com.lanchat.db.Phase2Demo

# Windows (PowerShell)
dir src -Recurse -Filter *.java | % { $_.FullName } > sources.txt
javac -d out @sources.txt
java -cp out com.lanchat.common.Phase1Demo
```
Or just open the folder in IntelliJ / Eclipse / VS Code and mark `src` as the source root.

## Folder layout
```
src/com/lanchat/
  common/   shared classes (User, Message, enums, Response)
  server/   ServerSocket, ClientHandler, broadcast   (Phase 3)
  client/   Swing UI + socket client                 (Phase 6)
  db/       JDBC + DAO classes                       (Phase 2)
  util/     password hashing, file helpers           (Phase 4)
lib/        JDBC driver jar
```

## Roadmap
- [x] Phase 1: entities, enums, Message hierarchy, Response<T>
- [x] Phase 2: database + DAO layer (SQLite, UserDAO, MessageDAO, PasswordUtil)
- [ ] Phase 3: multi-client server
- [ ] Phase 4: register / login over the network (SHA-256 hashing already in util/PasswordUtil)
- [ ] Phase 5: group + private chat
- [ ] Phase 6: Swing UI
- [ ] Phase 7: file transfer, history search, admin controls
- [ ] Phase 8: tests + README screenshots
