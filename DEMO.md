# Repospace - prototype demo notes (JavaFX application)

## 1. Run order for the presentation

| Step | What to run in NetBeans | What it proves |
|---|---|---|
| A | Run the project (F6) - main class `com.repospace.Repospace` | The working application: register, login, projects, repositories, commit, history, restore, public/private, search and download |
| B | Right-click the project > Test | Unit tests, including `EdgeCaseTest` (sandbox round trip), `SharingTest` and the database flow test |

If Run does nothing: right-click the project > Run Maven > Goals... and enter `javafx:run`.
Copy `sandbox/sample-project` to a new folder first so the original stays unchanged.

## 2. Script for step A (click through this in the window)

1. **Register** (link on the login screen): `alice`, `alice@example.com`, `password123`. Show that a short
   password and a username with a space are refused with a clear message. Then **log in**.
2. **Projects**: add `Thesis` / `Final year report` (private), set its status, make it public and private again.
3. **Repositories**: create `Notes` / `my notes`, **private**, **Browse...** to the copy of `sample-project`.
   Type `First version` and press **Commit**.
4. Edit `notes.txt` and add a file in the copied folder. Press **Check changes** (ADDED / MODIFIED), then
   **Commit** `Second version`. Click v2 and v1 in the history list to show what changed.
5. Select v1 and press **Restore selected version...** into a new empty folder. The file added later is not there.
6. Click **Make public** on the repository, then **Download as ZIP...** and open the ZIP.
7. **Profile**: write a bio, set visibility to **private**, save. Log out.
8. Register and log in as `bob`. Open **Discover**, search `ali`, select alice: her profile is private (only the
   name shows) but her public project and public repository are listed. Select the repository and download it
   as a ZIP or into an empty folder. Show that alice's private items are not listed.
9. **GitHub-style features** (as alice): open the repository with **Open repository page**. On *About and README*
   save a description, a website and topics `java, school`, write a README, press **Preview** then **Save**, and
   **Pin to my page**. *Releases*: publish `v1.0`, change a file, commit, publish `v2.0`; download v1.0 as a ZIP
   (it still has the old file). *Discussions*: start an Idea; *Wiki*: save `Home` with a `[[Install]]` link and
   click it. *Pages*: make the repository public, turn on Pages, open the site. On **Projects** select `Thesis`,
   **Open board**, add cards, move them forward and watch the progress bar. In **Edit profile** write a README
   such as `I made {{commits}} commits` and a status, then **My page**: badges, languages, pinned repositories,
   activity. Add a sponsor tier under **My page > My sponsor page**. As bob (Discover): search a topic in
   *Repositories* mode, open alice's page, post a reply, and pledge to a tier.
10. Switch **Settings > Theme** to Dark and back to show the themes.

Look inside the `data/` folder afterwards: `users.db`, `login.db`, `software_programme.db`,
`entry.db`, `logs/repospace.log` and `repos/<id>/objects/` (the stored snapshots).

## 3. UML to code (evidence)

* Every class, attribute and method of the UML class diagram exists in the source (see README,
  *Packages and the UML*, for the class-to-file table and the differences).
* The UML box named **Pouch** is now **Repospace** (renamed) - rename it in the diagram too.
* `Login` and `LoginPageController` are one class: `controller/LoginPageController.java`.
* The GUI uses the Repospace logo and its colours (see README, *Branding*).

## 4. Code review points

* Packages: `model` (UML domain classes), `controller` (screens), `database`, `util`.
* Every class and public method has a Javadoc comment; SQL uses prepared statements only.
* Passwords: salted PBKDF2 hash, never stored or logged in plain text.
* Commit writes to two databases; if the second write fails the first is removed again.
* Interface: `model/Persistable` (`getId()`, `save()`) is implemented by `Projects`, `Profile` and
  `Settings`, so one loop can save all three (see `persistableInterfaceWorksForEveryImplementor`).
* Known gaps to be honest about: line-by-line text diff is not implemented; the language setting is
  saved but screens are English only; empty folders are not tracked (only files are, as in Git);
  ownership checks for commit and delete are enforced by the menu and screens, while
  changing visibility and downloading are enforced inside the model classes; object serialization is not used.

## 5. Challenges faced

Technical (these actually came up while building it):

1. **Two UML diagrams for one system.** `Login` and `LoginPageController` described the same thing at two
   levels, so we merged them and documented the change.
2. **Storing many versions without wasting disk.** Solved with content-addressable storage (a file is
   saved once under its SHA-256 hash; a commit is only a path-to-hash list).
3. **Two databases that must agree.** Commits live in `software_programme.db` and file lists in
   `entry.db`; a failed second write now removes the first so history never points at missing data.
4. **Security of restore.** A damaged snapshot could contain a path such as `../x`; restore now refuses
   any path outside the target folder and any malformed hash.
5. **Window freezing on big folders.** Commit runs on a background `Task` in the GUI.
6. **Dependencies.** JavaFX and the SQLite driver come through Maven, so the first build needs internet.

Teamwork (draft - keep only the ones that match what really happened in your group, and change the wording
to your own experience before presenting):

1. **Coordinating eight people.** With eight members working on different classes at the same time, it was
   hard to know who was doing what. *What we did:* each member owns specific UML classes (see the Team Roles
   sheet) and progress is tracked weekly against the timeline.
2. **Merge conflicts in the shared repository.** Two people editing the same file caused conflicts when
   pushing to GitHub. *What we did:* one person owns each class, work happens on small commits, and one
   integration lead merges and tests the combined code.
3. **Different understandings of the UML.** Members read the two diagrams differently (for example,
   Login versus LoginPageController). *What we did:* we merged them into one class, agreed the method names
   as a group, and documented every difference from the UML.
4. **Dependencies between members.** Login, projects and repositories all need the database class first, so
   some members were waiting on others. *What we did:* the database class was built first and the others
   used placeholder code until it was ready.
5. **Finding meeting times.** Different class and personal timetables made full-team meetings difficult.
   *What we did:* short check-ins on a group chat, with one longer meeting before each deadline.
6. **Uneven experience levels.** Some members were more comfortable with Java and JavaFX than others.
   *What we did:* stronger members helped with setup, and the code review made sure everyone understood
   the classes they owned.
