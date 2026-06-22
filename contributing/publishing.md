# Publishing npm packages

`@launchdarkly/*` packages in this monorepo are published by the
[`turbo.yml`](../.github/workflows/turbo.yml) workflow using npm's
[trusted publishing (OIDC)](https://docs.npmjs.com/trusted-publishers) —
no long-lived npm token. Publishing runs on every push to `main` (and via
manual `workflow_dispatch`) through [`scripts/publish-npm.sh`](../scripts/publish-npm.sh),
which packs and publishes each publishable `@launchdarkly/*` workspace.

## Publishing a brand-new package for the first time

OIDC trusted publishing can only be configured on a package that **already
exists** on npm. The first CI publish of a new package therefore fails with:

```
npm error code E404
npm error 404 Not Found - PUT https://registry.npmjs.org/@launchdarkly%2f<pkg> - Not found
```

To establish the package before enabling OIDC, do this once:

### 1. Publish a placeholder

```
./scripts/publish-placeholder-package.sh sdk/@launchdarkly/<your-package>
```

The script logs you in/out of npm internally and publishes an empty `0.0.0`
version under the `snapshot` dist-tag, so it never becomes `latest`. You must
have publish rights to the `@launchdarkly` npm org.

### 2. Configure trusted publishing on npmjs

Follow
[the npm docs](https://docs.npmjs.com/trusted-publishers#configuring-trusted-publishing)
on the new package's Settings page, using these values:

| Field             | Value               |
| ----------------- | ------------------- |
| Publisher         | GitHub Actions      |
| Organization      | `launchdarkly`      |
| Repository        | `observability-sdk` |
| Workflow filename | `turbo.yml`         |

### 3. Mark the package public

On npmjs, set the package's access to public.

### 4. Release

Re-run the failed `Monorepo` publish (push to `main` or re-run the job). CI
now publishes the real version over the `0.0.0` placeholder via OIDC.
