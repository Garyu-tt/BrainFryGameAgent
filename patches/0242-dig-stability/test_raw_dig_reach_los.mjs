import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { EmbodiedAgency } from '../minecraft/embodied-agency.mjs'

const root=fs.mkdtempSync(path.join(os.tmpdir(),'brainfly-raw-dig-'))
fs.mkdirSync(path.join(root,'minecraft','memory'),{recursive:true})
const pos={x:0,y:64,z:0}
const blockPos={x:3,y:64,z:0}
const stale={name:'oak_log',type:1,position:blockPos,boundingBox:'block',digTime:()=>120}
const fresh={name:'oak_log',type:1,position:blockPos,boundingBox:'block',digTime:()=>120}
let navigations=0,digs=0,clears=0
const bot={
  username:'BrainFly',
  entity:{position:pos,height:1.8,effects:{}},
  game:{dimension:'overworld'},
  registry:{blocksByName:{oak_log:{id:1}},itemsByName:{oak_log:{id:101}}},
  inventory:{items:()=>[]},
  heldItem:null,
  findBlock:()=>stale,
  blockAt:p=>Number(p?.x)===3&&Number(p?.y)===64&&Number(p?.z)===0?fresh:null,
  canSeeBlock:()=>pos.x>=1,
  canDigBlock:()=>pos.x>=1,
  lookAt:async()=>{},
  clearControlStates(){clears++},
  setControlState(){},
  unequip:async()=>{},
  dig:async b=>{digs++;assert.equal(b,fresh,'must dig a freshly re-read world block, not stale findBlock result')}
}
const agency=new EmbodiedAgency({root,config:{navigation:{dig_settle_ms:0,dig_aim_settle_ms:0}},log:()=>{}})
agency.bot=bot
agency.navigateTo=async()=>{navigations++;pos.x=1;return {ok:true,reason:'test_arrived'}}
const r=await agency.acquireRaw('oak_log')
assert.equal(r.reason,'waiting_pickup',JSON.stringify(r))
assert.equal(navigations,1,'invisible target inside naive distance must trigger an approach')
assert.equal(digs,1)
assert.ok(clears>=1,'movement must be stopped before dig')
console.log('PASS raw dig reach/LOS stabilization',r)
