	.attribute	4, 16
	.attribute	5, "rv64i2p1_m2p0_a2p1_f2p2_d2p2_c2p0_zicsr2p0_zifencei2p0_zmmul1p0_zaamo1p0_zalrsc1p0_zca1p0_zcd1p0"
	.file	"prelude.c"
	.text
	.globl	__c_print_int                   # -- Begin function __c_print_int
	.p2align	1
	.type	__c_print_int,@function
__c_print_int:                          # @__c_print_int
	.cfi_startproc
# %bb.0:
.LC_pcrel_hi0:
	auipc	a1, %pcrel_hi(.LC_.str)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi0)
	mv	a2, a0
	mv	a0, a1
	mv	a1, a2
	tail	printf
.LC_func_end0:
	.size	__c_print_int, .LC_func_end0-__c_print_int
	.cfi_endproc
                                        # -- End function
	.globl	__c_println_int                 # -- Begin function __c_println_int
	.p2align	1
	.type	__c_println_int,@function
__c_println_int:                        # @__c_println_int
	.cfi_startproc
# %bb.0:
.LC_pcrel_hi1:
	auipc	a1, %pcrel_hi(.LC_.str.1)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi1)
	mv	a2, a0
	mv	a0, a1
	mv	a1, a2
	tail	printf
.LC_func_end1:
	.size	__c_println_int, .LC_func_end1-__c_println_int
	.cfi_endproc
                                        # -- End function
	.globl	__c_print_str                   # -- Begin function __c_print_str
	.p2align	1
	.type	__c_print_str,@function
__c_print_str:                          # @__c_print_str
	.cfi_startproc
# %bb.0:
.LC_pcrel_hi2:
	auipc	a1, %pcrel_hi(.LC_.str.2)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi2)
	mv	a2, a0
	mv	a0, a1
	mv	a1, a2
	tail	printf
.LC_func_end2:
	.size	__c_print_str, .LC_func_end2-__c_print_str
	.cfi_endproc
                                        # -- End function
	.globl	__c_println_str                 # -- Begin function __c_println_str
	.p2align	1
	.type	__c_println_str,@function
__c_println_str:                        # @__c_println_str
	.cfi_startproc
# %bb.0:
.LC_pcrel_hi3:
	auipc	a1, %pcrel_hi(.LC_.str.3)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi3)
	mv	a2, a0
	mv	a0, a1
	mv	a1, a2
	tail	printf
.LC_func_end3:
	.size	__c_println_str, .LC_func_end3-__c_println_str
	.cfi_endproc
                                        # -- End function
	.globl	__c_get_int                     # -- Begin function __c_get_int
	.p2align	1
	.type	__c_get_int,@function
__c_get_int:                            # @__c_get_int
	.cfi_startproc
# %bb.0:
	addi	sp, sp, -16
	.cfi_def_cfa_offset 16
	sd	ra, 8(sp)                       # 8-byte Folded Spill
	.cfi_offset ra, -8
.LC_pcrel_hi4:
	auipc	a0, %pcrel_hi(.LC_.str)
	addi	a0, a0, %pcrel_lo(.LC_pcrel_hi4)
	addi	a1, sp, 4
	call	scanf
	lw	a0, 4(sp)
	ld	ra, 8(sp)                       # 8-byte Folded Reload
	.cfi_restore ra
	addi	sp, sp, 16
	.cfi_def_cfa_offset 0
	ret
.LC_func_end4:
	.size	__c_get_int, .LC_func_end4-__c_get_int
	.cfi_endproc
                                        # -- End function
	.globl	__c_get_str                     # -- Begin function __c_get_str
	.p2align	1
	.type	__c_get_str,@function
__c_get_str:                            # @__c_get_str
	.cfi_startproc
# %bb.0:
.LC_pcrel_hi5:
	auipc	a1, %pcrel_hi(.LC_.str.4)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi5)
	mv	a2, a0
	mv	a0, a1
	mv	a1, a2
	tail	scanf
.LC_func_end5:
	.size	__c_get_str, .LC_func_end5-__c_get_str
	.cfi_endproc
                                        # -- End function
	.globl	__c_strlen                      # -- Begin function __c_strlen
	.p2align	1
	.type	__c_strlen,@function
__c_strlen:                             # @__c_strlen
	.cfi_startproc
# %bb.0:
	mv	a1, a0
	li	a0, -1
.LC_BB6_1:                                # =>This Inner Loop Header: Depth=1
	lbu	a2, 0(a1)
	addiw	a0, a0, 1
	addi	a1, a1, 1
	bnez	a2, .LC_BB6_1
# %bb.2:
	ret
.LC_func_end6:
	.size	__c_strlen, .LC_func_end6-__c_strlen
	.cfi_endproc
                                        # -- End function
	.globl	__c_strcpy                      # -- Begin function __c_strcpy
	.p2align	1
	.type	__c_strcpy,@function
__c_strcpy:                             # @__c_strcpy
	.cfi_startproc
# %bb.0:
	lbu	a2, 0(a1)
	beqz	a2, .LC_BB7_3
# %bb.1:
	li	a3, 0
.LC_BB7_2:                                # =>This Inner Loop Header: Depth=1
	add	a4, a0, a3
	sb	a2, 0(a4)
	add	a2, a1, a3
	lbu	a2, 1(a2)
	addi	a3, a3, 1
	bnez	a2, .LC_BB7_2
	j	.LC_BB7_4
.LC_BB7_3:
	li	a3, 0
.LC_BB7_4:
	add	a0, a0, a3
	sb	zero, 0(a0)
	ret
.LC_func_end7:
	.size	__c_strcpy, .LC_func_end7-__c_strcpy
	.cfi_endproc
                                        # -- End function
	.globl	__c_memfill                     # -- Begin function __c_memfill
	.p2align	1
	.type	__c_memfill,@function
__c_memfill:                            # @__c_memfill
	.cfi_startproc
# %bb.0:
	blez	a3, .LC_BB8_6
# %bb.1:
	li	t0, 0
	slli	a4, a2, 32
	srli	a6, a4, 32
	mv	a7, a0
	j	.LC_BB8_3
.LC_BB8_2:                                #   in Loop: Header=BB8_3 Depth=1
	addi	t0, t0, 1
	add	a7, a7, a6
	beq	t0, a3, .LC_BB8_6
.LC_BB8_3:                                # =>This Loop Header: Depth=1
                                        #     Child Loop BB8_5 Depth 2
	blez	a2, .LC_BB8_2
# %bb.4:                                #   in Loop: Header=BB8_3 Depth=1
	mul	a4, a6, t0
	add	a4, a4, a6
	add	t1, a0, a4
	mv	t2, a1
	mv	a5, a7
.LC_BB8_5:                                #   Parent Loop BB8_3 Depth=1
                                        # =>  This Inner Loop Header: Depth=2
	lbu	a4, 0(t2)
	sb	a4, 0(a5)
	addi	a5, a5, 1
	addi	t2, t2, 1
	bne	a5, t1, .LC_BB8_5
	j	.LC_BB8_2
.LC_BB8_6:
	ret
.LC_func_end8:
	.size	__c_memfill, .LC_func_end8-__c_memfill
	.cfi_endproc
                                        # -- End function
	.globl	__c_itoa                        # -- Begin function __c_itoa
	.p2align	1
	.type	__c_itoa,@function
__c_itoa:                               # @__c_itoa
	.cfi_startproc
# %bb.0:
	mv	a2, a1
.LC_pcrel_hi6:
	auipc	a1, %pcrel_hi(.LC_.str)
	addi	a1, a1, %pcrel_lo(.LC_pcrel_hi6)
	mv	a3, a0
	mv	a0, a2
	mv	a2, a3
	tail	sprintf
.LC_func_end9:
	.size	__c_itoa, .LC_func_end9-__c_itoa
	.cfi_endproc
                                        # -- End function
	.type	.LC_.str,@object                  # @.str
	.section	.rodata.str1.1,"aMS",@progbits,1
.LC_.str:
	.asciz	"%d"
	.size	.LC_.str, 3

	.type	.LC_.str.1,@object                # @.str.1
.LC_.str.1:
	.asciz	"%d\n"
	.size	.LC_.str.1, 4

	.type	.LC_.str.2,@object                # @.str.2
.LC_.str.2:
	.asciz	"%s"
	.size	.LC_.str.2, 3

	.type	.LC_.str.3,@object                # @.str.3
.LC_.str.3:
	.asciz	"%s\n"
	.size	.LC_.str.3, 4

	.type	.LC_.str.4,@object                # @.str.4
.LC_.str.4:
	.asciz	"%1023s"
	.size	.LC_.str.4, 7

	.ident	"clang version 22.1.6"
	.section	".note.GNU-stack","",@progbits
