(ns app.scale)

; SCALES = [                          # 1  2  3  4  5  6  7  8]
;   {name:"Major",              steps:[ 0, 2, 4, 5, 7, 9, 11,12 ]} # So so
;   {name:"Ukrainian Dorian",    steps:[ 0, 2, 3, 6, 7, 9, 10,12 ]} # Not great
;   {name:"Prometheus",          steps:[ 0, 2, 4, 6, 9, 10,12]}
;   {name:"Hirajoshi",          steps:[ 0, 2, 3, 7, 8, 12]}
;   {name:"Sakura Pentatonic",  steps:[ 0, 1, 5, 7, 8, 12 ]} # Sucks for drones
;   {name:"Iwato",              steps:[ 0, 1, 5, 6, 10,12 ]} # Very nice
;                                     # 1  2  3  4  5  6  7  8
;
; angular.module "Scale", []
;
; .service "Scale", (Control)->
;   scales = ((Math.pow(2, step/12) for step in scale.steps) for scale in SCALES)
;   current = null
;   index = null
;
;   do change = ()->
;     currentIndex = index
;     until index isnt currentIndex
;       index = Math.floor Math.random() * scales.length
;
;     current = scales[index]
;
;   get = (index)->
;     current[index % current.length]
;
;   scale =
;     change: change
;     get: get
;
