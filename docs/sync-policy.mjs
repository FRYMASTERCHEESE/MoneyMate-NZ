/** Destructive server writes are never allowed from a stale revision.
 * Deliberately return conflict rather than silently merging unlike device data. */
export function decideSync({enabled,baseRevision,baseDigest,localDigest,remoteRevision,localEmpty=false}) {
  if(!enabled) return "needs-consent";
  if(!Number.isSafeInteger(remoteRevision)||remoteRevision<0)throw Error("Invalid cloud revision");
  if(baseRevision!==remoteRevision) {
    if(baseDigest===localDigest || localEmpty) return "download";
    return "conflict";
  }
  if(baseDigest!==localDigest) return "upload";
  return "unchanged";
}
