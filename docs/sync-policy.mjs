/** A server snapshot already created by this account is safe to link on an empty device.
 * Never auto-link a device with any existing financial records or an invalid revision.
 */
export function shouldLinkExistingCloud({enabled=false,remoteRevision,localEmpty=false}) {
 if(!Number.isSafeInteger(remoteRevision)||remoteRevision<0)throw Error("Invalid cloud revision");
 return !enabled&&remoteRevision>0&&localEmpty;
}
/** Revision-checked whole-account snapshots. Never overwrite unsent local edits. */
export function decideSync({enabled,baseRevision,baseDigest,localDigest,remoteRevision,localEmpty=false}) {
 if(!enabled)return "needs-consent";
 if(!Number.isSafeInteger(baseRevision)||baseRevision<0||!Number.isSafeInteger(remoteRevision)||remoteRevision<0)throw Error("Invalid cloud revision");
 if(baseRevision!==remoteRevision){
  if(baseDigest===localDigest||(baseRevision===0&&localEmpty))return "download";
  return "conflict";
 }
 if(baseDigest!==localDigest)return "upload";
 return "unchanged";
}
