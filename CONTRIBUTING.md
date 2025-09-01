# How to contribute to JDiskMark

First of all, thank you for considering a contribution to JDiskMark. We really appreciate the time and effort you want to spend improving the project, and we can use the help

## What we are looking for

- documentation (Getting Started guide, Examples, etc...)
- bug reports
- bug fixes
- code quality improvements
- features (both ideas and code are welcome)

## Making your Changes

Before you start, we recommend you open an issue for new features or bug fixes. This helps us coordinate and avoids duplicate work.

1.  **Fork and Clone:** Start by forking the repository on GitHub and then clone your fork locally.

2.  **Create a New Branch:** From the `dev` branch create a new, descriptively named branch for your changes:
    `git checkout -b feature/your-awesome-feature`

3.  **Code & Test:**
    - Make your changes.
    - Add test cases for new features or bug fixes.
    - Test your build to make sure everything is still working.
    - Please ensure your code is formatted correctly. If you're using NetBeans, you can use `Alt` + `Shift` + `F`.

4.  **Commit Your Changes:**
    - Write a clear and concise commit message. For example, "Fix: Corrected null pointer exception in DiskLogger" or "Feat: Added support for XFS filesystem."
    - If you're fixing an issue, include the issue number and include `Closes#123` in your commit message to automatically close the issue when the PR is merged.
    - If you have multiple commits, please squash them into a single one before submitting your pull request.

## Submitting the Changes

Submit a pull request via the normal GitHub UI.

## After Submitting

Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.