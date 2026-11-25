import { describe, expect, it } from 'vitest'
import { bindSuccessMessage, validateBindForm } from './bind-student'

describe('validateBindForm', () => {
  it('学员姓名为空或全空格时报错', () => {
    expect(validateBindForm({ studentName: '', phone: '13800138000' })).toBe('请输入学员姓名')
    expect(validateBindForm({ studentName: '   ', phone: '13800138000' })).toBe('请输入学员姓名')
  })

  it('联系电话为空时报错', () => {
    expect(validateBindForm({ studentName: '张三', phone: '' })).toBe('请输入联系电话')
    expect(validateBindForm({ studentName: '张三', phone: '  ' })).toBe('请输入联系电话')
  })

  it('手机号位数不足或开头非法时报错', () => {
    expect(validateBindForm({ studentName: '张三', phone: '1380013800' })).toBe('请输入正确的 11 位手机号')
    expect(validateBindForm({ studentName: '张三', phone: '12800138000' })).toBe('请输入正确的 11 位手机号')
    expect(validateBindForm({ studentName: '张三', phone: '23800138000' })).toBe('请输入正确的 11 位手机号')
    expect(validateBindForm({ studentName: '张三', phone: '138001380000' })).toBe('请输入正确的 11 位手机号')
  })

  it('带前后空格的合法值可通过', () => {
    expect(validateBindForm({ studentName: '  张三  ', phone: ' 13800138000 ' })).toBeNull()
  })

  it('姓名超过 50 字符时报错', () => {
    expect(validateBindForm({ studentName: 'x'.repeat(51), phone: '13800138000' }))
      .toBe('学员姓名不能超过 50 个字符')
  })

  it('null / undefined 输入按空处理', () => {
    expect(validateBindForm({})).toBe('请输入学员姓名')
    expect(validateBindForm({ studentName: null, phone: null })).toBe('请输入学员姓名')
    expect(validateBindForm()).toBe('请输入学员姓名')
  })
})

describe('bindSuccessMessage', () => {
  it('注册来源返回专属文案', () => {
    expect(bindSuccessMessage(1, true)).toBe('注册并绑定成功')
  })

  it('非注册来源带当前孩子总数', () => {
    expect(bindSuccessMessage(2)).toBe('绑定成功，当前共 2 名孩子')
    expect(bindSuccessMessage(0)).toBe('绑定成功')
  })
})
