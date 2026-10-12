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
