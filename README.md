# Chisel3 FPU integration prototype

## 구현된 부분들

기초적인 부동 소수점 가감승제 구현

## Register map
#### Control register
제어 레지스터 주소: 0x4000\_0000

제어 레지스터 맵:

| Bit   | Description                         |
|-------|-------------------------------------|
| 0     | Start: 1이면 연산 시작              |
| 1     | Busy: 1이면 연산중                  |
| 2-7   | Reserved                            |
| 8-9   | Opcode. 0: Add 1: Sub 2: Mul 3: Div |
| 10-15 | Reserved                            |
| 16-31 | Reserved                            |

#### Data register

In A (operation) B = Y,

| Address     | Value |
|-------------|-------|
|0x4000\_0004 | \*A   |
|0x4000\_0008 | \*B   |
|0x4000\_000C | \*Y   |


## Errata

* 데이터 레지스터에 들어가는 주소는 반드시 버스 폭에 맞게 정렬되어 있어야 함.
