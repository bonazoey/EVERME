// Deterministic arithmetic experiment; no model calls or DB access.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const catalog=JSON.parse(fs.readFileSync(path.join(__dirname,'../../src/main/resources/clone-lab/catalog.json'),'utf8'));
function simulate(cap,scenario){
 let score=40,weight=cap;const values=[];
 const targets=Array.from({length:50},(_,i)=>scenario==='persistent-change'?60:40);
 const observations=targets.map((target,i)=>scenario==='single-outlier'&&i===0?80:target);
 for(const observed of observations){const effective=Math.min(weight,cap);score=Math.round(((score*effective+observed*2)/(effective+2))*100)/100;weight=Math.min(effective+2,cap);values.push(score);assert(weight<=cap);}
 return {meanAbsoluteError:values.reduce((sum,value,i)=>sum+Math.abs(value-targets[i]),0)/values.length,observationsToWithinTwo:values.findIndex((v,i)=>Math.abs(v-targets[i])<=2)+1||null,maximumDeviation:Math.max(...values.map((v,i)=>Math.abs(v-targets[i]))),finalScore:score};
}
const results={policyVersion:catalog.updatePolicyVersion,note:'Synthetic score sequences only; not human-clone accuracy.',elements:catalog.domains.flatMap(d=>d.elements).map(e=>({code:e.code,maxWeight:e.updatePolicy.maxWeight,scenarios:Object.fromEntries(['stable','persistent-change','single-outlier'].map(s=>[s,{baseline20:simulate(20,s),elementPolicy:simulate(e.updatePolicy.maxWeight,s)}]))}))};
assert.equal(results.elements.length,140);assert.equal(new Set(results.elements.map(e=>e.code)).size,140);
const output=path.join(__dirname,'../../build/clone-lab-evaluation/update-policies.json');fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(results,null,2)+'\n');console.log('PASS: 140 elements × 3 scenarios; report: '+output);
