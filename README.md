# Dantotsu Alpha Builds

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/Shebyyy/Dantotsu-Alpha?label=Latest%20Build&style=for-the-badge)](https://github.com/Shebyyy/Dantotsu-Alpha/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Shebyyy/Dantotsu-Alpha/beta.yml?branch=dev&label=Build%20Status&style=for-the-badge)](https://github.com/Shebyyy/Dantotsu-Alpha/actions)

This repository contains the automated build configuration for the **alpha version of the Dantotsu application**.

> **What is Dantotsu?**
> Dantotsu is a feature-rich Android client for anime streaming, designed to provide a seamless and enhanced viewing experience.

---

## 📥 How to Download

Alpha builds are generated manually and are not officially released. You can download the latest APK directly from the **Actions** tab of this repository.

1.  Navigate to the [**Actions**](https://github.com/Shebyyy/Dantotsu-Alpha/actions) page.
2.  Select the most recent workflow run from the list.
3.  Under the "Artifacts" section, you will find a file named `Dantotsu.apk`.
4.  Click the download button to get the latest build.




---

## 🔄 Side-by-Side Installation

This alpha build is modified to install as a **separate application** from the stable version of Dantotsu.

This means you can have both versions installed on your device at the same time without any conflicts. The alpha build will have a slightly different name (e.g., "Santotsu") and a unique package ID to ensure this separation.

---

## ⚠️ Important Disclaimer

Please be aware that these are **alpha builds**. They are intended for testing and preview purposes only.

*   **Unstable:** These builds may contain bugs, crashes, or unfinished features.
*   **Not for Daily Use:** They are not recommended for daily use by the average user.
*   **No Support:** Official support is not provided for alpha versions. Use them at your own risk.

For a stable experience, please use the official release version of Dantotsu.

---

## 🔧 How It Works

This repository uses **GitHub Actions** to automatically build the Dantotsu application whenever a new build is triggered.

The process is as follows:
1.  **Checkout:** The workflow fetches the latest source code from the main project's Gitea repository.
2.  **Patch & Modify:** It applies necessary patches to fix dependency issues and modifies the app's name and package ID to allow for side-by-side installation.
3.  **Build:** The project is compiled and signed to produce an APK file.
4.  **Upload:** The final APK is uploaded as a build artifact for easy access.

---

## 🐛 Reporting Issues

If you encounter a problem while using an alpha build, please report it on the **main project's issue tracker**.

**[Click here to report an issue on Gitea](https://git.rebelonion.dev/rebelonion/Dantotsu/issues)**

Please provide as much detail as possible, including steps to reproduce the issue and your device information.

---

## 🤝 Credits

*   **Dantotsu:** All credit for the application itself goes to the original developers.
*   **Source Code:** The official source code is hosted on [Gitea](https://git.rebelonion.dev/rebelonion/Dantotsu).

---

## 📄 License

This project, including the automation scripts and build process, is licensed under the same license as the original Dantotsu application. Please refer to the main project repository for license details.
