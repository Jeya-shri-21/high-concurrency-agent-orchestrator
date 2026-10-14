# Zaalima Development Pvt. Ltd. — GitHub Workflow & Eclipse IDE Guide

**Project:** High-Concurrency Autonomous Agent Orchestrator  
**Document Author:** Member 4 (REST API & GitHub Workflow)  
**Applicability:** All Development Team Members  

---

## 1. Team Git Workflow & Branch Management

### A. The Core Golden Rule
> [!IMPORTANT]
> **NEVER commit or push directly to `main`.**  
> All active development must occur exclusively on individual feature branches. Direct pushes to `main` risk overwriting teammates' work, breaking ongoing builds, and violating Zaalima Development SOPs.

### B. Individual Branch Naming Convention
Branches must strictly follow the team naming format:
```text
<member_name>-week<number>-<feature_name>
```
* **Member 1 (Core Backend):** `shri-week1-backend`
* **Member 2 (Virtual Thread Task Handling):** `pujitha-week1-virtual-thread-task-handling`
* **Member 3 (gRPC Communication):** `kaushal-week1-grpc`
* **Member 4 (REST API & Git Protocol):** `deepansh-week1-rest-github`

### C. Commit Message Standards (Conventional Commits)
All commit messages must be clear, concise, and adhere to Conventional Commits:
```text
<type>(<scope>): <short description in present tense>

[optional body explaining motivation and impact]
```

#### Allowed Types:
* `feat`: A new feature (e.g., `feat(rest): implement agent registration endpoint`)
* `fix`: A bug fix (e.g., `fix(registry): handle duplicate agent ID conflict`)
* `test`: Adding or correcting tests (e.g., `test(rest): add WebMvc unit tests for registration`)
* `docs`: Documentation updates (e.g., `docs: add REST API usage and Git workflow guide`)
* `refactor`: Code refactoring without changing behavior (e.g., `refactor(dto): extract error response payload`)
* `chore`: Build or tooling updates (e.g., `chore(pom): add validation starter dependency`)

---

## 2. Staging Hygiene: What to Stage and What Never to Stage

### Clean Staging Policy
Only source code, configuration files, and documentation should be tracked by Git.

####  Files to Stage:
* Java source code under `src/main/java/` and `src/test/java/`
* Configuration files under `src/main/resources/` (e.g., `application.properties`)
* Protocol buffer definitions under `src/main/proto/`
* Root configuration files (`pom.xml`, `.gitignore`, `README.md`, `mvnw`, `.mvn/`)
* Project documentation (`MEMBER4-README.md`, `GIT-WORKFLOW-GUIDE.md`)

#### ❌ Files NEVER to Stage:
* **Maven build output:** `target/`, `*.jar`, `*.war`, `*.class`
* **Eclipse IDE project metadata:** `.project`, `.classpath`, `.settings/`, `.factorypath`, `bin/`
* **IntelliJ / VS Code files:** `.idea/`, `*.iml`, `.vscode/`
* **Operating System files:** `Thumbs.db`, `.DS_Store`
* **Credentials or Secrets:** API keys, passwords, private keys (`*.pem`, `*.key`)

> [!TIP]
> Ensure your repository's `.gitignore` contains exclusions for `target/`, `.project`, `.classpath`, and `.settings/`. Before staging, always run `git status` to verify that no IDE or build files are listed.

---

## 3. Commit Signing (GPG) Standards & Accurate Verification

### A. Important Technical Distinctions

To ensure security compliance, every developer must understand the technical differences between these concepts:

1. **Signed Commit:** A Git commit created with the `-S` flag that contains a cryptographic GPG or SSH signature embedded in the commit metadata.
2. **Cryptographically Verified Signature:** A signature verified locally on the developer's machine using `git log --show-signature` or `git verify-commit <hash>` against the local GPG keyring.
3. **GitHub "Verified" Badge:** A visual badge displayed on GitHub web UI when:
   * The commit is signed with a valid GPG/SSH key.
   * The public key is uploaded to the author's GitHub account (`Settings -> SSH and GPG keys`).
   * The committer's email in Git matches a verified email address on the GitHub account.
4. **Enforced Signature Rule (Branch Protection):** An administrative repository setting on GitHub (`Require signed commits`) that actively rejects any `git push` containing unsigned commits.

> [!NOTE]
> Signing your commits is a local Git operation. If GitHub branch protection has not yet enforced commit signing, your branch will still push successfully, but configuring signing ensures your contributions display GitHub's green "Verified" badge.

---

### B. Configuring GPG Commit Signing on Windows

#### Step 1: Install GPG (GnuPG)
If you have Git for Windows installed, GPG is already included! Verify with:
```bash
gpg --version
```

#### Step 2: Generate a GPG Key Pair
Run the following in Git Bash:
```bash
gpg --full-generate-key
```
* **Key type:** `(1) RSA and RSA` (default)
* **Keysize:** `4096`
* **Expiration:** `0` (key does not expire) or your chosen duration
* **Real name:** `Deepansh Dharmik`
* **Email address:** `deepanshdharmik9@gmail.com` (must match your Git email and GitHub account email)
* Set a secure passphrase when prompted.

#### Step 3: Find Your Key ID
```bash
gpg --list-secret-keys --keyid-format=long
```
Output example:
```text
sec   rsa4096/3AA5C34371567BD2 2026-10-14 [SC]
      ...
uid                 [ultimate] Deepansh Dharmik <deepanshdharmik9@gmail.com>
```
Your Key ID in this example is `3AA5C34371567BD2`.

#### Step 4: Configure Git to Use Your Key
```bash
git config --global user.signingkey <YOUR_KEY_ID>
git config --global commit.gpgsign true
```

#### Step 5: Export Public Key to GitHub
Export your public key:
```bash
gpg --armor --export <YOUR_KEY_ID>
```
1. Copy the output (including `-----BEGIN PGP PUBLIC KEY BLOCK-----` and `-----END PGP PUBLIC KEY BLOCK-----`).
2. Go to **GitHub -> Settings -> SSH and GPG keys -> New GPG key**.
3. Paste the key and click **Add GPG key**.

#### Step 6: Verify Local Signatures
```bash
git log --show-signature -n 1
```
Output will indicate:
`gpg: Good signature from "Deepansh Dharmik <deepanshdharmik9@gmail.com>"`

---

## 4. Eclipse IDE on Windows: Step-by-Step Developer Guide

### Step 1: Clone Repository
Open **Git Bash** on Windows and run:
```bash
cd /c/Users/LENOVO/Desktop
git clone https://github.com/Jeya-shri-21/high-concurrency-agent-orchestrator.git
```
*(Or use your existing local clone at `C:\Users\LENOVO\Desktop\high-concurrency-agent-orchestrator\high-concurrency-agent-orchestrator`)*

---

### Step 2: Import into Eclipse IDE
1. Open **Eclipse IDE**.
2. Go to **File -> Import...**
3. Select **Maven -> Existing Maven Projects** and click **Next**.
4. In **Root Directory**, click **Browse...** and select:
   `C:\Users\LENOVO\Desktop\high-concurrency-agent-orchestrator\high-concurrency-agent-orchestrator`
5. Ensure `pom.xml` is checked in the Projects list.
6. Click **Finish**. Eclipse will import the project and resolve Maven dependencies.

---

### Step 3: Configure Java 21 / 23 in Eclipse
1. Go to **Window -> Preferences**.
2. Navigate to **Java -> Installed JREs**.
3. If JDK 21 or JDK 23 is not listed, click **Add... -> Standard VM -> Next**.
4. Set **JRE home** to `C:\Program Files\Java\jdk-23` (or your JDK 21 directory).
5. Click **Finish** and check the box next to the newly added JDK.
6. Navigate to **Java -> Compiler** and ensure **Compiler compliance level** is set to `21`.
7. Click **Apply and Close**.

---

### Step 4: Switch to Your Feature Branch in Eclipse
1. Right-click the project root in **Package Explorer** or **Project Explorer**.
2. Select **Team -> Switch To -> Other...**
3. Expand **Local** and select `deepansh-week1-rest-github`.
4. Click **Checkout**.
*(If using Git Bash: `git checkout deepansh-week1-rest-github`)*

---

### Step 5: Refresh and Resolve Maven Build
If any red error icons appear on the project:
1. Right-click the project -> **Maven -> Update Project...** (or press `Alt + F5`).
2. Check **Force Update of Snapshots/Releases**.
3. Click **OK**.

---

### Step 6: Run the Spring Boot Application
1. In Eclipse Package Explorer, navigate to:
   `src/main/java/com/zaalima/orchestrator/VirtualThreadTaskHandlingApplication.java`
2. Right-click the file -> **Run As -> Java Application** (or **Spring Boot App**).
3. The console will display the Spring Boot banner and confirm the server has started on port `8080` (and gRPC server port).

---

### Step 7: Test REST Endpoints
You can test the endpoints using PowerShell, curl, or Postman:

**Register an Agent:**
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/agents" `
  -Headers @{"Content-Type"="application/json"} `
  -Body '{"agentId":"agent-001","agentType":"WORKER"}'
```

**Get Registered Agent:**
```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/agents/agent-001"
```

**List All Registered Agents:**
```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/agents"
```

---

### Step 8: Run Automated Tests in Eclipse
1. In Package Explorer, right-click `src/test/java`.
2. Select **Run As -> JUnit Test**.
3. Eclipse JUnit view will run all 28 test cases with green checkmarks across:
   * `AgentRegistrationControllerTest`
   * `AgentRegistryServiceTest`
   * `AgentGrpcServiceTest`
   * `AgentGrpcServiceIntegrationTest`

---

### Step 9: Staging & Committing in Eclipse Git Staging View
1. Open the Git Staging view: **Window -> Show View -> Other... -> Git -> Git Staging**.
2. Under **Unstaged Changes**, select only the intended files:
   * `src/main/java/com/zaalima/orchestrator/...`
   * `src/test/java/com/zaalima/orchestrator/...`
   * `pom.xml`, `.gitignore`, `MEMBER4-README.md`, `GIT-WORKFLOW-GUIDE.md`
3. Drag them to **Staged Changes** (or click the `+` button).
4. In **Commit Message**, write a clear message adhering to Conventional Commits:
   ```text
   feat(rest): implement agent registration REST API and GitHub workflow
   ```
5. Click **Commit** (or **Commit and Push...**).

---

### Step 10: Pushing to Your Feature Branch
> [!WARNING]
> Never push to `main`! Push only to your assigned remote branch: `origin/deepansh-week1-rest-github`.

From Git Bash:
```bash
git push origin deepansh-week1-rest-github
```

From Eclipse:
1. Right-click project -> **Team -> Push Branch 'deepansh-week1-rest-github'...**
2. Confirm Remote is `origin` and branch is `deepansh-week1-rest-github`.
3. Click **Push**.

---

## 5. Pull Request (PR) & Team Lead Merge Protocol

1. Once your individual branch is pushed to GitHub, navigate to the repository on GitHub:
   `https://github.com/Jeya-shri-21/high-concurrency-agent-orchestrator`
2. Click **Compare & pull request** next to `deepansh-week1-rest-github`.
3. Set the base branch to `main` and compare branch to `deepansh-week1-rest-github`.
4. Title the PR according to the feature (e.g., `feat(rest): Member 4 agent registration REST API and workflow guide`).
5. Include a description outlining:
   * REST endpoints added (`/api/agents`).
   * Tests added (`AgentRegistrationControllerTest`, `AgentRegistryServiceTest`).
   * Verification of zero regressions across Member 2 and Member 3 components.
6. Request a review from the team leader (`Jeya-shri-21`).
7. **Only the team leader or designated reviewer merges the PR into `main`.**
