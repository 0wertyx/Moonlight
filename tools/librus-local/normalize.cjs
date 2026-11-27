"use strict";

function normalize(grades, timetable, monday) {
  const days=["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"];
  if (!Array.isArray(grades) || !timetable || !Array.isArray(timetable.hours) || !timetable.table || typeof timetable.table!=="object") throw Error("Unsupported response shape");
  if (!grades.length && !Object.keys(timetable.table).length) throw Error("Empty response: login or parser may have failed");
  const result={schemaVersion:1,lessons:[],grades:[]};
  let skipped=0;
  const seen=new Set();
  for (const subject of grades) {
    if(typeof subject?.name!=="string" || !subject.name.trim() || subject.name.trim().length>100 || !Array.isArray(subject.semester)) throw Error("Invalid grade group");
    for (const term of subject.semester) {
      if (!Array.isArray(term?.grades)) throw Error("Invalid semester");
      for(const g of term.grades) {
        if(typeof g?.value!=="string" || !g.value.trim() || g.value.trim().length>20){skipped++;continue;}
        if(Number.isSafeInteger(g.id)&&g.id>0){if(seen.has(g.id))continue;seen.add(g.id);}
        result.grades.push({subject:subject.name.trim(),value:g.value.trim(),weight:null});
      }
    }
  }
  for(const [i,day] of days.entries()){
    const rows=timetable.table[day];
    if(rows===undefined)continue;
    if(!Array.isArray(rows)||rows.length>20)throw Error("Unsupported timetable rows");
    const date=new Date(monday+"T00:00:00Z");date.setUTCDate(date.getUTCDate()+i);
    for(const [index,row] of rows.entries()){
      if(row===null)continue;
      if(typeof row?.subject!=="string"||!row.subject.trim()||row.subject.trim().length>100){skipped++;continue;}
      const flag=typeof row.flag==="string"?row.flag.toLocaleLowerCase("pl"):"";
      let status="scheduled";
      if(row.cancelled===true){
        if(!flag.includes("odwołan")){skipped++;continue;}
        status="cancelled";
      }else if(flag.includes("zastępstw"))status="substitution";
      result.lessons.push({date:date.toISOString().slice(0,10),subject:row.subject.trim(),period:index+1,status});
    }
  }
  if(result.lessons.length>2000||result.grades.length>2000)throw Error("Too many entries");
  return {snapshot:result,skipped};
}

module.exports = { normalize };
