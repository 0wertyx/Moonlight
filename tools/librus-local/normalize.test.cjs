"use strict";
const {test}=require("node:test");
const assert=require("node:assert/strict");
const {normalize}=require("./normalize.cjs");
test("converts own-account data without inventing weights or cancellation",()=>{
 const r=normalize([{name:"Math",semester:[{grades:[{id:1,value:"5"},{id:2,value:"4+"}]},{grades:[{id:1,value:"5"}]}]}],{hours:["08:00","09:00","10:00"],table:{Monday:[{subject:"Math",cancelled:false},{subject:"History",cancelled:true,flag:"odwołane"},{subject:"Other",cancelled:true,flag:"przeniesione"}]}},"2026-11-23");
 assert.equal(r.snapshot.grades.length,2);
 assert.equal(r.snapshot.grades[0].weight,null);
 assert.equal(r.snapshot.lessons[1].status,"cancelled");
 assert.equal(r.skipped,1);
});
test("rejects suspiciously empty response",()=>assert.throws(()=>normalize([],{hours:[],table:{}},"2026-11-23")));
