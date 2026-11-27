# Experimental local Librus export
This is a separate local Node.js utility, not Android login or a background server. It calls the public interface of the unofficial librus-api package (2.20.0, MIT, upstream https://github.com/Mati365/librus-api). Use only your own account and only where permitted by the service. The upstream warns that systematic collection may violate the terms. No scheduled network requests, retries, school-record edits, message actions, credential storage or third-party backend are included. Do not put school data or credentials in GitHub or CI.

With Node.js 22.13+ installed, in the tools/librus-local directory:
```
npm install --ignore-scripts
npm test
npm start
```
The last command is an interactive one-shot export, not a verified live login. If the official service rejects login or requests a verification step, stop and use the official portal. The terminal asks for a password without displaying it; no credentials are passed as arguments. Copy only your own exported JSON to the phone privately and select it in Moonlight's data-source tab. The utility exports one week and available grades; weights are unknown, period is the row order (not a verified school lesson number), ambiguous cancellations are omitted, and grade IDs are not exported. Existing Android UI marks the file as an unverified local copy. No actual school account has been tested. The pinned library's dependencies still require normal security review before real account use.
