"use strict";

const readline = require("node:readline/promises");
const { Writable } = require("node:stream");
const { writeFile } = require("node:fs/promises");
const { homedir } = require("node:os");
const { join } = require("node:path");
const { normalize } = require("./normalize.cjs");

function weekInPoland() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Warsaw", year:"numeric", month:"2-digit", day:"2-digit"
  }).formatToParts(new Date());
  const p = Object.fromEntries(parts.map(({type,value}) => [type,value]));
  const day = new Date(p.year+"-"+p.month+"-"+p.day+"T00:00:00Z");
  day.setUTCDate(day.getUTCDate() - (day.getUTCDay()+6)%7);
  const monday=day.toISOString().slice(0,10);
  day.setUTCDate(day.getUTCDate()+6);
  return [monday,day.toISOString().slice(0,10)];
}

async function credentials() {
  if(!process.stdin.isTTY || !process.stdout.isTTY) throw Error("Interactive terminal required");
  let muted=false;
  const out=new Writable({write(chunk, encoding, callback) {
    if(!muted) process.stdout.write(chunk, encoding);
    callback();
  }});
  const rl=readline.createInterface({input:process.stdin,output:out,terminal:true});
  try {
    const ok=await rl.question("Only your own account, if the service permits this client. Continue? [y/N] ");
    if(ok.trim().toLowerCase()!=="y") return null;
    const login=(await rl.question("Synergia login: ")).trim();
    process.stdout.write("Password (hidden): ");
    muted=true;
    const password=await rl.question("");
    muted=false;process.stdout.write("\n");
    if(!login || !password)throw Error("Missing credentials");
    return {login,password};
  } finally { muted=false;rl.close(); }
}

async function main(){
  let stage="initialization";
  try {
    const Librus=require("librus-api");
    const auth=await credentials();
    if(!auth)return;
    const client=new Librus(undefined,{requestTimeout:20000});
    stage="authorization";
    try {await client.authorize(auth.login,auth.password);}
    finally {auth.login="";auth.password="";}
    stage="grades";
    const grades=await client.info.getGrades();
    stage="timetable";
    const [from,to]=weekInPoland();
    const timetable=await client.calendar.getTimetable(from,to);
    stage="validation";
    const {snapshot,skipped}=normalize(grades,timetable,from);
    const json=JSON.stringify(snapshot,null,2);
    if(Buffer.byteLength(json)>512*1024)throw Error("Too large");
    stage="local export";
    const path=join(homedir(),"moonlight-"+Date.now()+".json");
    await writeFile(path,json,{encoding:"utf8",mode:0o600,flag:"wx"});
    console.log("Saved local snapshot:",path);
    console.log("Grades:",snapshot.grades.length,"Lessons:",snapshot.lessons.length,"Skipped ambiguous entries:",skipped);
  }catch(_){
    // Never print HTTP error objects, request headers, cookies, or credentials.
    console.error("Failed at:",stage+". No retries; use the official portal if login is rejected.");
    process.exitCode=1;
  }
}
main();
