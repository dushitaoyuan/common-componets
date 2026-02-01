/**
 * JSON 对比工具类
 */
class JSONDiffTool {
    constructor() {
        this.diff2htmlUi = null;

    }

    /**
     * 格式化 JSON 对象为字符串
     * @param {Object} obj - JSON 对象
     * @returns {string} 格式化后的 JSON 字符串
     */
    formatJSON(obj) {
        return JSON.stringify(obj, null, 2);
    }

    /**
     * 解析 JSON 字符串
     * @param {string} jsonString - JSON 字符串
     * @returns {Object} 解析后的对象
     * @throws {Error} 解析错误
     */
    parseJSON(jsonString) {
        try {
            // 移除首尾空白字符
            jsonString = jsonString.trim();
            if (!jsonString) {
                throw new Error('JSON 字符串为空');
            }
            return JSON.parse(jsonString);
        } catch (e) {
            throw new Error(`JSON 解析错误: ${e.message}`);
        }
    }

    /**
     * 生成统一差异格式
     * @param {string} oldStr - 原始字符串
     * @param {string} newStr - 新字符串
     * @returns {string} 统一差异格式字符串
     */
    createUnifiedDiff(oldStr, newStr) {
        return Diff.createTwoFilesPatch(
            'before.json',
            'after.json',
            oldStr,
            newStr,
            '原始数据',
            '变更后数据'
        );
    }

    /**
     * 渲染差异到 HTML
     * @param {string} diffString - 统一差异字符串
     * @param {HTMLElement} container - 容器元素
     */
    renderDiff(diffString, container) {
        // 使用 diff2html 渲染差异
        const targetElement = container;
        const configuration = {
            drawFileList: true,
            fileListToggle: false,
            fileListStartVisible: false,
            fileContentToggle: false,
            matching: 'lines',
            outputFormat: 'side-by-side',
            synchronisedScroll: true,
            highlight: true,
            renderNothingWhenEmpty: false,
        };

        const diff2htmlUi = new Diff2HtmlUI(targetElement, diffString, configuration);
        diff2htmlUi.draw();
        diff2htmlUi.highlightCode();

        this.diff2htmlUi = diff2htmlUi;
    }

    /**
     * 对比两个 JSON 对象
     * @param {string|Object} json1 - 原始 JSON
     * @param {string|Object} json2 - 对比 JSON
     * @returns {Object} 对比结果
     */
    compare(json1, json2) {
        try {
            // 解析 JSON
            const obj1 = typeof json1 === 'string' ? this.parseJSON(json1) : json1;
            const obj2 = typeof json2 === 'string' ? this.parseJSON(json2) : json2;

            // 格式化为字符串
            const str1 = this.formatJSON(obj1);
            const str2 = this.formatJSON(obj2);

            // 生成差异
            const diff = this.createUnifiedDiff(str1, str2);

            return {
                success: true,
                diff: diff,
                original: str1,
                modified: str2
            };
        } catch (error) {
            return {
                success: false,
                error: error.message
            };
        }
    }
}

// 创建全局实例
const jsonDiffTool = new JSONDiffTool();
