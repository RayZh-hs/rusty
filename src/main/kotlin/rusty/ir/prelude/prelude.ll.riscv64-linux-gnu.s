	.attribute	4, 16
	.attribute	5, "rv64i2p1_m2p0_a2p1_f2p2_d2p2_c2p0_zicsr2p0_zifencei2p0_zmmul1p0_zaamo1p0_zalrsc1p0_zca1p0_zcd1p0"
	.file	"prelude.ll"
	.text
	.globl	prelude.String.len              # -- Begin function prelude.String.len
	.p2align	1
	.type	prelude.String.len,@function
prelude.String.len:                     # @prelude.String.len
	.cfi_startproc
# %bb.0:                                # %entry
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	ld	a0, 0(a0)
	call	__c_strlen
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end0:
	.size	prelude.String.len, .Lfunc_end0-prelude.String.len
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.print              # -- Begin function prelude.func.print
	.p2align	1
	.type	prelude.func.print,@function
prelude.func.print:                     # @prelude.func.print
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	call	__c_print_str
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end1:
	.size	prelude.func.print, .Lfunc_end1-prelude.func.print
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.println            # -- Begin function prelude.func.println
	.p2align	1
	.type	prelude.func.println,@function
prelude.func.println:                   # @prelude.func.println
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	call	__c_println_str
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end2:
	.size	prelude.func.println, .Lfunc_end2-prelude.func.println
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.printInt           # -- Begin function prelude.func.printInt
	.p2align	1
	.type	prelude.func.printInt,@function
prelude.func.printInt:                  # @prelude.func.printInt
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
                                        # kill: def $x11 killed $x10
	call	__c_print_int
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end3:
	.size	prelude.func.printInt, .Lfunc_end3-prelude.func.printInt
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.printlnInt         # -- Begin function prelude.func.printlnInt
	.p2align	1
	.type	prelude.func.printlnInt,@function
prelude.func.printlnInt:                # @prelude.func.printlnInt
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
                                        # kill: def $x11 killed $x10
	call	__c_println_int
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end4:
	.size	prelude.func.printlnInt, .Lfunc_end4-prelude.func.printlnInt
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.getInt             # -- Begin function prelude.func.getInt
	.p2align	1
	.type	prelude.func.getInt,@function
prelude.func.getInt:                    # @prelude.func.getInt
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	call	__c_get_int
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end5:
	.size	prelude.func.getInt, .Lfunc_end5-prelude.func.getInt
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.getString          # -- Begin function prelude.func.getString
	.p2align	1
	.type	prelude.func.getString,@function
prelude.func.getString:                 # @prelude.func.getString
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	call	__c_get_str
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end6:
	.size	prelude.func.getString, .Lfunc_end6-prelude.func.getString
	.cfi_endproc
                                        # -- End function
	.globl	prelude.func.exit               # -- Begin function prelude.func.exit
	.p2align	1
	.type	prelude.func.exit,@function
prelude.func.exit:                      # @prelude.func.exit
# %bb.0:                                # %entry
	addi	sp, sp, -16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
                                        # kill: def $x11 killed $x10
	call	exit
.Lfunc_end7:
	.size	prelude.func.exit, .Lfunc_end7-prelude.func.exit
                                        # -- End function
	.globl	aux.func.memfill                # -- Begin function aux.func.memfill
	.p2align	1
	.type	aux.func.memfill,@function
aux.func.memfill:                       # @aux.func.memfill
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
                                        # kill: def $x14 killed $x13
                                        # kill: def $x14 killed $x12
	call	__c_memfill
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end8:
	.size	aux.func.memfill, .Lfunc_end8-aux.func.memfill
	.cfi_endproc
                                        # -- End function
	.globl	aux.func.itoa                   # -- Begin function aux.func.itoa
	.p2align	1
	.type	aux.func.itoa,@function
aux.func.itoa:                          # @aux.func.itoa
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
                                        # kill: def $x12 killed $x10
	call	__c_itoa
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end9:
	.size	aux.func.itoa, .Lfunc_end9-aux.func.itoa
	.cfi_endproc
                                        # -- End function
	.globl	main                            # -- Begin function main
	.p2align	1
	.type	main,@function
main:                                   # @main
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
	call	user.func.main
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.Lfunc_end10:
	.size	main, .Lfunc_end10-main
	.cfi_endproc
                                        # -- End function
	.section	".note.GNU-stack","",@progbits
