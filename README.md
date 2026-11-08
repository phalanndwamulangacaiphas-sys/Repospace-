# Repospace

**A simple version control system for students and small teams.**

Repospace is a desktop application written in Java and JavaFX. It watches a folder on your computer and lets you save numbered snapshots of it (*commits*), look back at every change, restore any earlier version, and share finished work with other users. Think of it as a small, friendly Git with a graphical interface.

Built for **SDN260S Software Design II** (topic 19: *Simple Version Control System*), Cape Peninsula University of Technology.

---

## Contents

1. [Features](#1-features)
2. [Requirements](#2-requirements)
3. [Installation and running](#3-installation-and-running)
4. [User guide](#4-user-guide)
5. [Public and private items](#5-public-and-private-items)
6. [GitHub-style features](#6-github-style-features)
7. [Where your data is stored](#7-where-your-data-is-stored)
8. [Running the tests](#8-running-the-tests)
9. [Troubleshooting](#9-troubleshooting)
10. [Project structure](#10-project-structure)
11. [How it works](#11-how-it-works)
12. [Known limitations](#12-known-limitations)

---

## 1. Features

| Area | What you can do |
|---|---|
| Accounts | Register, log in with username or e-mail, edit your profile and settings |
| Version control | Commit a folder with a message, see exactly which files were added, modified or deleted, restore any version, clone or download the latest version as a ZIP |
| Sharing | Make projects, repositories and your profile public or private; search for other users; view their public profiles; download their public repositories |
| Extras | Repository README and About box, pinned repositories, releases, wiki, discussions, project boards, sponsor tiers, a local Pages website, profile statistics and badges |
| Look and feel | Light and dark themes in the Repospace logo colours |

---

## 2. Requirements

| Needed | Version | Notes |
|---|---|---|
| Java JDK | 17 or newer (21 recommended) | The JDK, not just the JRE |
| Apache NetBeans | 17 or newer | Maven support is built in. Any IDE that opens Maven projects also works |
| Apache Maven | 3.8 or newer | Included with NetBeans; install it separately only if you build from a terminal |
| Internet connection | First build only | Maven downloads JavaFX 21.0.2, the SQLite driver 3.45.1.0 and JUnit 5.10.2 |
| Screen | 1024 x 768 or larger | The window opens at 980 x 640 |
| Disk space | About 200 MB for dependencies, plus space for your snapshots | |

No database server is needed. SQLite creates its own files the first time the program starts.

---

## 3. Installation and running

### Option A: Apache NetBeans (recommended)

1. Unzip the project. You should have a folder named `Repospace` that contains `pom.xml`.
2. In NetBeans choose **File > Open Project...** and select that folder.
3. Wait while NetBeans downloads the dependencies (progress bar at the bottom right).
4. Right-click the project and choose **Clean and Build**.
5. Press **Run** (F6). If NetBeans asks for a main class, choose `com.repospace.Repospace`.
   If Run does nothing: right-click the project > **Run Maven > Goals...**, type `javafx:run`, and press OK.

### Option B: Terminal

```bash
cd Repospace
mvn clean javafx:run
```

To build without running: `mvn clean package`.

### Try it with sample data

The `sandbox/` folder holds practice material:

* `sandbox/sample-project` is a small normal folder to track.
* `sandbox/edge-cases` holds an empty file, a binary file, a file name with spaces, a file five folders deep and an empty folder.

**Copy a folder out of `sandbox/` before tracking it**, so the original stays unchanged.

---

## 4. User guide

### 4.1 Create an account and log in

1. On the login screen click **Create a new account**.
2. Fill in:
   * **Username:** 3 to 30 characters. Letters, digits, `.`, `_` and `-` only. **No spaces.** Example: `khuthadzo.makuya`.
   * **E-mail:** a valid address, for example `name@example.com`.
   * **Password:** at least 8 characters.
3. Press **Create an account**, then log in with your username **or** your e-mail address and your password.

Your password is stored only as a salted hash, never as plain text.

### 4.2 Home screen

After logging in you see the home screen with a sidebar:

| Button | Opens |
|---|---|
| Home | Welcome page |
| Projects | Your project list and project boards |
| Repositories | Version control: commit, history, restore, download |
| Discover | Search for other users and public repositories |
| My page | Your public page: README, pinned repositories, badges, activity |
| Edit profile | Bio, picture, README, status and profile visibility |
| Settings | Theme, language preference, notifications |
| Log out | Ends your session |

### 4.3 Create a repository and make your first commit

1. Click **Repositories**, then **New repository**.
2. Enter a **name**, choose **Public** or **Private** (private is the default), press **Browse...** to select the folder you want to track, and press **Create repository**.
3. Select the repository in the list, type a message such as `First version`, and press **Commit**.
   Your first commit is stored as **v1**.

Large folders are processed in the background, so the window stays responsive.

### 4.4 Work with versions

| Action | How |
|---|---|
| Save a new version | Change files in the tracked folder, press **Check changes** to preview (`ADDED`, `MODIFIED`, `DELETED`), then type a message and press **Commit**. You get v2, v3 and so on |
| See what a version changed | Click a version in the history list. You see who made it, when, and which files were added, modified or deleted |
| Go back to an older version | Select the version and press **Restore selected version...**. Choose an **empty** folder; the old files are written there. Your tracked folder is not changed |
| Copy the latest version | **Clone latest...** copies the newest version into an empty folder |
| Download as a ZIP | **Download as ZIP...** saves the newest version as one `.zip` file. Works for your own repositories and for other users' public ones |
| Browse files | **View files** lists the files of the latest version |
| Change visibility | **Make public** or **Make private** (owner only) |
| Remove a repository | **Delete repository** deletes Repospace's saved versions. **Your original folder is never touched** |

### 4.5 Projects

Use **Projects** as a to-do list for your work.

1. Enter a name and description, choose **Public** or **Private**, set a status and press **Add project**.
2. Select a project and press **Set status of selected** to change its status, or use **Make selected public / private** to change who can see it (making a project public asks you to confirm).
3. Press **Open board** (or double-click the project) to see its kanban board with **To do**, **In progress** and **Done** columns, cards and a progress bar.

### 4.6 Find other people and download their work

1. Open **Discover**. Choose **People** or **Repositories** in the drop-down.
2. Type part of a username (or, in Repositories mode, a name, description or topic) and press **Search**.
3. Select a user to see their profile, their **public** projects and their **public** repositories.
4. Select a public repository and choose **Download as ZIP...** or **Download to a folder...**.

Private items are never listed and cannot be downloaded. Other users never see your e-mail address.

### 4.7 Your profile and settings

* **Edit profile:** change your username, e-mail, bio and picture; write a profile README and a status line; choose **Public** or **Private**. A private profile shows other users only your username.
* **Settings:** switch between **Light** and **Dark** themes, set your language preference and turn notifications on or off. (The language setting is saved, but the screens are in English.)

---

## 5. Public and private items

| Item | Default | When PUBLIC | When PRIVATE |
|---|---|---|---|
| Project | Private | Every user can see it on your Discover page | Only you |
| Repository | Private | Every user can browse its history and download the latest version | Only you |
| Profile | Public | Every user can see your bio, picture and member-since date | Others see only your username |
| E-mail and password | Never shown | | Only you can see your e-mail |

Only the owner can change visibility. The rule is enforced inside the program's model classes, not only by hiding buttons, so another user cannot change your settings.

---

## 6. GitHub-style features

These work inside Repospace on your own computer. Where GitHub needs the internet or money, Repospace keeps a local version.

| Feature | How to use it |
|---|---|
| **Profile README and status** | **Edit profile**, write Markdown and a status, press **Save changes**, then open **My page**. Placeholders such as `{{commits}}`, `{{repos}}`, `{{streak}}`, `{{longest-streak}}` and `{{top-language}}` are replaced by live numbers |
| **Repository About and README** | Open a repository with **Open repository page**. Add a description (up to 350 characters), a website and up to 10 topics (lowercase letters, digits and `-`). The README shown is `README.md` from your latest commit, otherwise a README written in Repospace |
| **Pinned repositories** | Press **Pin to my page** on a repository. You can pin up to 6 |
| **Releases** | On the **Releases** tab enter a tag such as `v1.0` and press **Publish release**. It is fixed to the newest version and its ZIP never changes, even after more commits. Pre-releases are supported |
| **Wiki** | On the **Wiki** tab write several pages. Link pages with `[[Page title]]`. Revisions are kept. Only the owner can edit |
| **Discussions** | On the **Discussions** tab start a thread in a category (Announcements, General, Ideas, Q&A, Show and tell) and reply. Only the owner posts announcements; the owner or author can close or delete a thread |
| **Project boards** | **Projects > Open board**. Add cards, move them between columns, optionally link a card to a repository |
| **Sponsors** | On your page press **Sponsor**, then **Add tier** (up to 5 tiers). Other users choose a tier and press **Pledge to this tier**. Only pledges are recorded; no money moves |
| **Pages** | On the **Pages** tab of a **public** repository press **Turn on Pages**, then **Open the site in my browser** (the address looks like `http://localhost:8765/<username>/<repository>/`). It shows `index.html` if there is one, otherwise the README. It works only on your computer while Repospace is open |
| **Statistics and badges** | **My page** shows repositories, commits, current and longest streak, top language, releases and recent activity. Badges are also available as pictures, for example `http://localhost:8765/badges/<username>/commits.svg` |

Markdown supports headings, bold, italic, `code`, lists, quotes, code blocks, links and `[[wiki links]]`. It never runs scripts and only keeps `http`, `https` and `mailto` links. Images are shown as text such as `[image: description]`.

---

## 7. Where your data is stored

Repospace creates a `data/` folder next to where it runs:

| Path | Contents |
|---|---|
| `data/users.db` | Users, profiles, settings, sponsor tiers and pledges |
| `data/login.db` | Sign-in attempts |
| `data/software_programme.db` | Projects, boards, repositories, commits, history, pins, discussions, releases, wiki pages |
| `data/entry.db` | The file list (manifest) of each commit |
| `data/repos/<id>/objects/` | The saved copies of your files, named by their SHA-256 hash |
| `data/logs/repospace.log` | Activity and error log |

To use a different location, start Repospace with `-Drepospace.data=/path/to/folder`.

**To back up your work, copy the whole `data/` folder.** To start again from scratch, close Repospace and delete it.

---

## 8. Running the tests

In NetBeans, right-click the project and choose **Test** (Alt+F6), or in a terminal run:

```bash
mvn test
```

There are 77 automated JUnit 5 tests in `src/test/java/com/repospace`:

| Test class | Covers |
|---|---|
| `PasswordUtilTest` | Password hashing and verification |
| `FileUtilTest` | Recursive listing, SHA-256, recursive delete |
| `SnapshotStoreTest` | Snapshots, de-duplication, diff, restore, unsafe path refusal |
| `EdgeCaseTest` | Empty, binary and oddly named files, deep folders, 500 files |
| `MarkdownTest` | Markdown parsing and safety |
| `VersionControlFlowTest` | Register, log in, projects, commit, history, restore, delete |
| `SharingTest` | Public/private rules, user search, downloads, database upgrade |
| `CollaborationTest` | Pins, discussions, boards, releases, wiki, sponsors |
| `ProfileFeaturesTest` | Profile README, About, statistics, badges, Pages server |

---

## 9. Troubleshooting

| Problem | What to do |
|---|---|
| **Run does nothing in NetBeans** | Right-click the project > **Run Maven > Goals...**, type `javafx:run` |
| **Dependencies will not download** | Check your internet connection for the first build, then **Clean and Build** again |
| **"Release version 17 not supported" or similar** | Your JDK is older than 17. In NetBeans open **Tools > Java Platforms**, add JDK 17 or newer, and select it in the project properties |
| **Registration says the username is invalid** | Use 3 to 30 characters: letters, digits, `.`, `_`, `-`. No spaces |
| **Registration says the password is too short** | Use at least 8 characters, for example `password123` |
| **Restore says the folder must be empty** | Choose a new or empty folder. Repospace will not write over existing files |
| **Download as ZIP asks for a file name** | Type a file name ending in `.zip`, not just a folder |
| **A repository has nothing to download** | Make at least one commit first |
| **Pages link does not open** | Pages must be turned on, the repository must be **public**, and Repospace must still be running. If port 8765 is busy, Repospace picks another one; use the link shown in the Pages tab |
| **I cannot see another user's repository** | It is private. Only the owner can see or download it |
| **Something went wrong and I need details** | Open `data/logs/repospace.log` |

---

## 10. Project structure

```
Repospace/
  pom.xml                       Maven build (Java 17, JavaFX 21.0.2, SQLite, JUnit 5)
  README.md                     This file
  DEMO.md                       Presentation script
  sandbox/                      Sample folders to practise with
  src/main/java/com/repospace/
    Repospace.java              Application entry point
    controller/                 JavaFX screen logic (Login, Register, Home, Projects,
                                Repository, Discover, Profile, Settings, RepoPage,
                                UserPage, Board, Sponsors, Navigator, MarkdownView)
    model/                      Business rules: User, Profile, Settings, Projects,
                                Repository, Commit, History, Release, Discussion, WikiPage,
                                BoardTask, SponsorTier, Sponsorship, PinnedRepositories,
                                ProfileStats, Badge, Persistable
    database/                   DatabaseConnection (connections, schema, upgrades)
    util/                       SnapshotStore, FileUtil, PasswordUtil, Markdown,
                                PagesServer, ActivityLogger, DateUtil, Session, Branding
  src/main/resources/com/repospace/
    view/                       FXML layouts
    css/                        light.css and dark.css
    images/                     Logo and icons
  src/test/java/com/repospace/  JUnit tests
```

---

## 11. How it works

* **Content-addressable storage.** Each file is stored once under its SHA-256 hash. A commit is a *manifest* (file path to hash), so unchanged files cost no extra space between versions.
* **Diff.** Two manifests are compared to list `ADDED`, `MODIFIED` and `DELETED` files.
* **Recursion.** `FileUtil.collect` walks folder trees; `deleteRecursively` removes them; the Markdown reader handles nested styles recursively.
* **Collections.** Sorted `TreeMap` manifests, `TreeSet` of commit days for streaks, an `ArrayDeque` for the Back button.
* **Concurrency.** Commits run on a background `javafx.concurrent.Task`; the Pages server uses a small pool of daemon threads and listens on `127.0.0.1` only.
* **Security.** Passwords use salted PBKDF2-HMAC-SHA256. All SQL uses prepared statements. Restore and ZIP export refuse paths that would escape the target folder. Permission checks live in the model classes.
* **Design.** Layered MVC: FXML views, controllers, model, then utilities and database.

---

## 12. Known limitations

* Differences are shown per file (added, modified, deleted), not line by line.
* No branches or merging.
* Empty folders are not tracked (only files are, as in Git).
* Restoring writes the old files into a folder; it does not delete other files.
* The language setting is saved, but the screens are in English only.
* Sponsors record pledges only; no payments are made.
* Pages are visible only on your own computer while Repospace is open.
* There are no collaborators: only the owner edits a repository's About, releases and wiki.
* Images in a README are shown as text.

---

*Repospace, SDN260S Software Design II group project, Cape Peninsula University of Technology.*
